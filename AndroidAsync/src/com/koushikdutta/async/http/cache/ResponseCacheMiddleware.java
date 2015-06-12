package com.koushikdutta.async.http.cache;

import android.net.Uri;
import android.util.Base64;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.SimpleMiddleware;
import com.koushikdutta.async.util.Allocator;
import com.koushikdutta.async.util.Charsets;
import com.koushikdutta.async.util.FileCache;
import com.koushikdutta.async.util.StreamUtility;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.CacheResponse;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLEngine;

public class ResponseCacheMiddleware extends SimpleMiddleware {
    public static final int ENTRY_METADATA = 0;
    public static final int ENTRY_BODY = 1;
    public static final int ENTRY_COUNT = 2;
    public static final String SERVED_FROM = "X-Served-From";
    public static final String CONDITIONAL_CACHE = "conditional-cache";
    public static final String CACHE = "cache";
    private static final String LOGTAG = "AsyncHttpCache";
    private boolean caching = true;
    private int writeSuccessCount;
    private int writeAbortCount;
    private FileCache cache;
    private AsyncServer server;
    private int conditionalCacheHitCount;
    private int cacheHitCount;
    private int networkCount;
    private int cacheStoreCount;

    private ResponseCacheMiddleware() {
    }

    public static ResponseCacheMiddleware addCache(AsyncHttpClient client, File cacheDir, long size) throws IOException {
        for (AsyncHttpClientMiddleware middleware: client.getMiddleware()) {
            if (middleware instanceof ResponseCacheMiddleware)
                throw new IOException("Response cache already added to http client");
        }
        ResponseCacheMiddleware ret = new ResponseCacheMiddleware();
        ret.server = client.getServer();
        ret.cache = new FileCache(cacheDir, size, false);
        client.insertMiddleware(ret);
        return ret;
    }

    public FileCache getFileCache() {
        return cache;
    }
    
    public boolean getCaching() {
        return caching;
    }
    
    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    // step 1) see if we can serve request from the cache directly.
    // also see if this can be turned into a conditional cache request.
    @Override
    public Cancellable getSocket(final GetSocketData data) {
        RequestHeaders requestHeaders = new RequestHeaders(data.request.getUri(), RawHeaders.fromMultimap(data.request.getHeaders().getMultiMap()));
        data.state.put("request-headers", requestHeaders);

        if (cache == null || !caching || requestHeaders.isNoCache()) {
            networkCount++;
            return null;
        }

        String key = FileCache.toKeyString(data.request.getUri());
        FileInputStream[] snapshot = null;
        long contentLength;
        Entry entry;
        try {
            snapshot = cache.get(key, ENTRY_COUNT);
            if (snapshot == null) {
                networkCount++;
                return null;
            }
            contentLength = snapshot[ENTRY_BODY].available();
            entry = new Entry(snapshot[ENTRY_METADATA]);
        }
        catch (IOException e) {
            // Give up because the cache cannot be read.
            networkCount++;
            StreamUtility.closeQuietly(snapshot);
            return null;
        }

        // verify the entry matches
        if (!entry.matches(data.request.getUri(), data.request.getMethod(), data.request.getHeaders().getMultiMap())) {
            networkCount++;
            StreamUtility.closeQuietly(snapshot);
            return null;
        }

        EntryCacheResponse candidate = new EntryCacheResponse(entry, snapshot[ENTRY_BODY]);

        Map<String, List<String>> responseHeadersMap;
        FileInputStream cachedResponseBody;
        try {
            responseHeadersMap = candidate.getHeaders();
            cachedResponseBody = candidate.getBody();
        }
        catch (Exception e) {
            networkCount++;
            StreamUtility.closeQuietly(snapshot);
            return null;
        }
        if (responseHeadersMap == null || cachedResponseBody == null) {
            networkCount++;
            StreamUtility.closeQuietly(snapshot);
            return null;
        }

        RawHeaders rawResponseHeaders = RawHeaders.fromMultimap(responseHeadersMap);
        ResponseHeaders cachedResponseHeaders = new ResponseHeaders(data.request.getUri(), rawResponseHeaders);
        rawResponseHeaders.set("Content-Length", String.valueOf(contentLength));
        rawResponseHeaders.removeAll("Content-Encoding");
        rawResponseHeaders.removeAll("Transfer-Encoding");
        cachedResponseHeaders.setLocalTimestamps(System.currentTimeMillis(), System.currentTimeMillis());

        long now = System.currentTimeMillis();
        ResponseSource responseSource = cachedResponseHeaders.chooseResponseSource(now, requestHeaders);

        if (responseSource == ResponseSource.CACHE) {
            data.request.logi("Response retrieved from cache");
            final CachedSocket socket = entry.isHttps() ? new CachedSSLSocket(candidate, contentLength) : new CachedSocket(candidate, contentLength);
            socket.pending.add(ByteBuffer.wrap(rawResponseHeaders.toHeaderString().getBytes()));

            server.post(new Runnable() {
                @Override
                public void run() {
                    data.connectCallback.onConnectCompleted(null, socket);
                    socket.sendCachedDataOnNetworkThread();
                }
            });
            cacheHitCount++;
            data.state.put("socket-owner", this);
            SimpleCancellable ret = new SimpleCancellable();
            ret.setComplete();
            return ret;
        }
        else if (responseSource == ResponseSource.CONDITIONAL_CACHE) {
            data.request.logi("Response may be served from conditional cache");
            CacheData cacheData = new CacheData();
            cacheData.snapshot = snapshot;
            cacheData.contentLength = contentLength;
            cacheData.cachedResponseHeaders = cachedResponseHeaders;
            cacheData.candidate = candidate;
            data.state.put("cache-data", cacheData);
            return null;
        }
        else {
            data.request.logd("Response can not be served from cache");
            // NETWORK or other
            networkCount++;
            StreamUtility.closeQuietly(snapshot);
            return null;
        }
    }

    public int getConditionalCacheHitCount() {
        return conditionalCacheHitCount;
    }

    public int getCacheHitCount() {
        return cacheHitCount;
    }
    
    public int getNetworkCount() {
        return networkCount;
    }

    public int getCacheStoreCount() {
        return cacheStoreCount;
    }

    // step 2) if this is a conditional cache request, serve it from the cache if necessary
    // otherwise, see if it is cacheable
    @Override
    public void onBodyDecoder(OnBodyDataOnRequestSentData data) {
        CachedSocket cached = com.koushikdutta.async.Util.getWrappedSocket(data.socket, CachedSocket.class);
        if (cached != null) {
            data.response.headers().set(SERVED_FROM, CACHE);
            return;
        }

        CacheData cacheData = data.state.get("cache-data");
        RawHeaders rh = RawHeaders.fromMultimap(data.response.headers().getMultiMap());
        rh.removeAll("Content-Length");
        rh.setStatusLine(String.format("%s %s %s", data.response.protocol(), data.response.code(), data.response.message()));
        ResponseHeaders networkResponse = new ResponseHeaders(data.request.getUri(), rh);
        data.state.put("response-headers", networkResponse);
        if (cacheData != null) {
            if (cacheData.cachedResponseHeaders.validate(networkResponse)) {
                data.request.logi("Serving response from conditional cache");
                ResponseHeaders combined = cacheData.cachedResponseHeaders.combine(networkResponse);
                data.response.headers(new Headers(combined.getHeaders().toMultimap()));
                data.response.code(combined.getHeaders().getResponseCode());
                data.response.message(combined.getHeaders().getResponseMessage());

                data.response.headers().set(SERVED_FROM, CONDITIONAL_CACHE);
                conditionalCacheHitCount++;

                CachedBodyEmitter bodySpewer = new CachedBodyEmitter(cacheData.candidate, cacheData.contentLength);
                bodySpewer.setDataEmitter(data.bodyEmitter);
                data.bodyEmitter = bodySpewer;
                bodySpewer.sendCachedData();
                return;
            }

            // did not validate, so fall through and cache the response
            data.state.remove("cache-data");
            StreamUtility.closeQuietly(cacheData.snapshot);
        }

        if (!caching)
            return;

        RequestHeaders requestHeaders = data.state.get("request-headers");
        if (requestHeaders == null || !networkResponse.isCacheable(requestHeaders) || !data.request.getMethod().equals(AsyncHttpGet.METHOD)) {
            /*
             * Don't cache non-GET responses. We're technically allowed to cache
             * HEAD requests and some POST requests, but the complexity of doing
             * so is high and the benefit is low.
             */
            networkCount++;
            data.request.logd("Response is not cacheable");
            return;
        }

        String key = FileCache.toKeyString(data.request.getUri());
        RawHeaders varyHeaders = requestHeaders.getHeaders().getAll(networkResponse.getVaryFields());
        Entry entry = new Entry(data.request.getUri(), varyHeaders, data.request, networkResponse.getHeaders());
        BodyCacher cacher = new BodyCacher();
        EntryEditor editor = new EntryEditor(key);
        try {
            entry.writeTo(editor);
            // create the file
            editor.newOutputStream(ENTRY_BODY);
        }
        catch (Exception e) {
            // Log.e(LOGTAG, "error", e);
            editor.abort();
            networkCount++;
            return;
        }
        cacher.editor = editor;

        cacher.setDataEmitter(data.bodyEmitter);
        data.bodyEmitter = cacher;

        data.state.put("body-cacher", cacher);
        data.request.logd("Caching response");
        cacheStoreCount++;
    }

    // step 3: close up shop
    @Override
    public void onResponseComplete(OnResponseCompleteDataOnRequestSentData data) {
        CacheData cacheData = data.state.get("cache-data");
        if (cacheData != null && cacheData.snapshot != null)
            StreamUtility.closeQuietly(cacheData.snapshot);

        CachedSocket cachedSocket = Util.getWrappedSocket(data.socket, CachedSocket.class);
        if (cachedSocket != null)
            StreamUtility.closeQuietly((cachedSocket.cacheResponse).getBody());

        BodyCacher cacher = data.state.get("body-cacher");
        if (cacher != null) {
            if (data.exception != null)
                cacher.abort();
            else
                cacher.commit();
        }
    }
    
    public void clear() {
        if (cache != null) {
            cache.clear();
        }
    }

    public static class CacheData {
        FileInputStream[] snapshot;
        EntryCacheResponse candidate;
        long contentLength;
        ResponseHeaders cachedResponseHeaders;
    }
    
    private static class BodyCacher extends FilteredDataEmitter {
        EntryEditor editor;
        ByteBufferList cached;

        @Override
        protected void report(Exception e) {
            super.report(e);
            if (e != null)
                abort();
        }

        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            if (cached != null) {
                super.onDataAvailable(emitter, cached);
                // couldn't emit it all, so just wait for another day...
                if (cached.remaining() > 0)
                    return;
                cached = null;
            }

            // write to cache... any data not consumed needs to be retained for the next callback
            ByteBufferList copy = new ByteBufferList();
            try {
                if (editor != null) {
                    OutputStream outputStream = editor.newOutputStream(ENTRY_BODY);
                    if (outputStream != null) {
                        while (!bb.isEmpty()) {
                            ByteBuffer b = bb.remove();
                            try {
                                ByteBufferList.writeOutputStream(outputStream, b);
                            }
                            finally {
                                copy.add(b);
                            }
                        }
                    }
                    else {
                        abort();
                    }
                }
            }
            catch (Exception e) {
                abort();
            }
            finally {
                bb.get(copy);
                copy.get(bb);
            }

            super.onDataAvailable(emitter, bb);

            if (editor != null && bb.remaining() > 0) {
                cached = new ByteBufferList();
                bb.get(cached);
            }
        }

        @Override
        public void close() {
            abort();
            super.close();
        }

        public void abort() {
            if (editor != null) {
                editor.abort();
                editor = null;
            }
        }

        public void commit() {
            if (editor != null) {
                editor.commit();
                editor = null;
            }
        }
    }

    private static class CachedBodyEmitter extends FilteredDataEmitter {
        EntryCacheResponse cacheResponse;
        ByteBufferList pending = new ByteBufferList();
        private boolean paused;
        private Allocator allocator = new Allocator();
        boolean allowEnd;
        public CachedBodyEmitter(EntryCacheResponse cacheResponse, long contentLength) {
            this.cacheResponse = cacheResponse;
            allocator.setCurrentAlloc((int)contentLength);
        }

        Runnable sendCachedDataRunnable = new Runnable() {
            @Override
            public void run() {
                sendCachedDataOnNetworkThread();
            }
        };

        void sendCachedDataOnNetworkThread() {
            if (pending.remaining() > 0) {
                super.onDataAvailable(CachedBodyEmitter.this, pending);
                if (pending.remaining() > 0)
                    return;
            }

            // fill pending
            try {
                ByteBuffer buffer = allocator.allocate();
                assert buffer.position() == 0;
                FileInputStream din = cacheResponse.getBody();
                int read = din.read(buffer.array(), buffer.arrayOffset(), buffer.capacity());
                if (read == -1) {
                    ByteBufferList.reclaim(buffer);
                    allowEnd = true;
                    report(null);
                    return;
                }
                allocator.track(read);
                buffer.limit(read);
                pending.add(buffer);
            }
            catch (IOException e) {
                allowEnd = true;
                report(e);
                return;
            }
            super.onDataAvailable(this, pending);
            if (pending.remaining() > 0)
                return;
            // this limits max throughput to 256k (aka max alloc) * 100 per second...
            // roughly 25MB/s
            getServer().postDelayed(sendCachedDataRunnable, 10);
        }

        void sendCachedData() {
            getServer().post(sendCachedDataRunnable);
        }

        @Override
        public void resume() {
            paused = false;
            sendCachedData();
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public void close() {
            StreamUtility.closeQuietly(cacheResponse.getBody());
            super.close();
        }

        @Override
        protected void report(Exception e) {
            // a 304 response will immediate call report/end since there is no body.
            // prevent this from happening by waiting for the actual body to be spit out.
            if (!allowEnd)
                return;
            StreamUtility.closeQuietly(cacheResponse.getBody());
            super.report(e);
        }
    }
    
    private static final class Entry {
        private final String uri;
        private final RawHeaders varyHeaders;
        private final String requestMethod;
        private final RawHeaders responseHeaders;
        private final String cipherSuite;
        private final Certificate[] peerCertificates;
        private final Certificate[] localCertificates;

        /*
         * Reads an entry from an input stream. A typical entry looks like this:
         *   http://google.com/foo
         *   GET
         *   2
         *   Accept-Language: fr-CA
         *   Accept-Charset: UTF-8
         *   HTTP/1.1 200 OK
         *   3
         *   Content-Type: image/png
         *   Content-Length: 100
         *   Cache-Control: max-age=600
         *
         * A typical HTTPS file looks like this:
         *   https://google.com/foo
         *   GET
         *   2
         *   Accept-Language: fr-CA
         *   Accept-Charset: UTF-8
         *   HTTP/1.1 200 OK
         *   3
         *   Content-Type: image/png
         *   Content-Length: 100
         *   Cache-Control: max-age=600
         *
         *   AES_256_WITH_MD5
         *   2
         *   base64-encoded peerCertificate[0]
         *   base64-encoded peerCertificate[1]
         *   -1
         *
         * The file is newline separated. The first two lines are the URL and
         * the request method. Next is the number of HTTP Vary request header
         * lines, followed by those lines.
         *
         * Next is the response status line, followed by the number of HTTP
         * response header lines, followed by those lines.
         *
         * HTTPS responses also contain SSL session information. This begins
         * with a blank line, and then a line containing the cipher suite. Next
         * is the length of the peer certificate chain. These certificates are
         * base64-encoded and appear each on their own line. The next line
         * contains the length of the local certificate chain. These
         * certificates are also base64-encoded and appear each on their own
         * line. A length of -1 is used to encode a null array.
         */
        public Entry(InputStream in) throws IOException {
            StrictLineReader reader = null;
            try {
                reader = new StrictLineReader(in, Charsets.US_ASCII);
                uri = reader.readLine();
                requestMethod = reader.readLine();
                varyHeaders = new RawHeaders();
                int varyRequestHeaderLineCount = reader.readInt();
                for (int i = 0; i < varyRequestHeaderLineCount; i++) {
                    varyHeaders.addLine(reader.readLine());
                }

                responseHeaders = new RawHeaders();
                responseHeaders.setStatusLine(reader.readLine());
                int responseHeaderLineCount = reader.readInt();
                for (int i = 0; i < responseHeaderLineCount; i++) {
                    responseHeaders.addLine(reader.readLine());
                }

//                if (isHttps()) {
//                    String blank = reader.readLine();
//                    if (blank.length() != 0) {
//                        throw new IOException("expected \"\" but was \"" + blank + "\"");
//                    }
//                    cipherSuite = reader.readLine();
//                    peerCertificates = readCertArray(reader);
//                    localCertificates = readCertArray(reader);
//                } else {
                    cipherSuite = null;
                    peerCertificates = null;
                    localCertificates = null;
//                }
            } finally {
                StreamUtility.closeQuietly(reader, in);
            }
        }

        public Entry(Uri uri, RawHeaders varyHeaders, AsyncHttpRequest request, RawHeaders responseHeaders) {
            this.uri = uri.toString();
            this.varyHeaders = varyHeaders;
            this.requestMethod = request.getMethod();
            this.responseHeaders = responseHeaders;

//            if (isHttps()) {
//                HttpsURLConnection httpsConnection = (HttpsURLConnection) httpConnection;
//                cipherSuite = httpsConnection.getCipherSuite();
//                Certificate[] peerCertificatesNonFinal = null;
//                try {
//                    peerCertificatesNonFinal = httpsConnection.getServerCertificates();
//                } catch (SSLPeerUnverifiedException ignored) {
//                }
//                peerCertificates = peerCertificatesNonFinal;
//                localCertificates = httpsConnection.getLocalCertificates();
//            } else {
                cipherSuite = null;
                peerCertificates = null;
                localCertificates = null;
//            }
        }

        public void writeTo(EntryEditor editor) throws IOException {
            OutputStream out = editor.newOutputStream(ENTRY_METADATA);
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, Charsets.UTF_8));

            writer.write(uri + '\n');
            writer.write(requestMethod + '\n');
            writer.write(Integer.toString(varyHeaders.length()) + '\n');
            for (int i = 0; i < varyHeaders.length(); i++) {
                writer.write(varyHeaders.getFieldName(i) + ": "
                        + varyHeaders.getValue(i) + '\n');
            }

            writer.write(responseHeaders.getStatusLine() + '\n');
            writer.write(Integer.toString(responseHeaders.length()) + '\n');
            for (int i = 0; i < responseHeaders.length(); i++) {
                writer.write(responseHeaders.getFieldName(i) + ": "
                        + responseHeaders.getValue(i) + '\n');
            }

            if (isHttps()) {
                writer.write('\n');
                writer.write(cipherSuite + '\n');
                writeCertArray(writer, peerCertificates);
                writeCertArray(writer, localCertificates);
            }
            writer.close();
        }

        private boolean isHttps() {
            return uri.startsWith("https://");
        }

        private Certificate[] readCertArray(StrictLineReader reader) throws IOException {
            int length = reader.readInt();
            if (length == -1) {
                return null;
            }
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                Certificate[] result = new Certificate[length];
                for (int i = 0; i < result.length; i++) {
                    String line = reader.readLine();
                    byte[] bytes = Base64.decode(line, Base64.DEFAULT);
                    result[i] = certificateFactory.generateCertificate(
                            new ByteArrayInputStream(bytes));
                }
                return result;
            } catch (CertificateException e) {
                throw new IOException(e.getMessage());
            }
        }

        private void writeCertArray(Writer writer, Certificate[] certificates) throws IOException {
            if (certificates == null) {
                writer.write("-1\n");
                return;
            }
            try {
                writer.write(Integer.toString(certificates.length) + '\n');
                for (Certificate certificate : certificates) {
                    byte[] bytes = certificate.getEncoded();
                    String line = Base64.encodeToString(bytes, Base64.DEFAULT);
                    writer.write(line + '\n');
                }
            } catch (CertificateEncodingException e) {
                throw new IOException(e.getMessage());
            }
        }

        public boolean matches(Uri uri, String requestMethod,
                Map<String, List<String>> requestHeaders) {
            return this.uri.equals(uri.toString())
                    && this.requestMethod.equals(requestMethod)
                    && new ResponseHeaders(uri, responseHeaders)
                            .varyMatches(varyHeaders.toMultimap(), requestHeaders);
        }
    }

    static class EntryCacheResponse extends CacheResponse {
        private final Entry entry;
        private final FileInputStream snapshot;

        public EntryCacheResponse(Entry entry, FileInputStream snapshot) {
            this.entry = entry;
            this.snapshot = snapshot;
        }

        @Override public Map<String, List<String>> getHeaders() {
            return entry.responseHeaders.toMultimap();
        }

        @Override public FileInputStream getBody() {
            return snapshot;
        }
    }

    private class CachedSSLSocket extends CachedSocket implements AsyncSSLSocket {
        public CachedSSLSocket(EntryCacheResponse cacheResponse, long contentLength) {
            super(cacheResponse, contentLength);
        }

        @Override
        public SSLEngine getSSLEngine() {
            return null;
        }

        @Override
        public X509Certificate[] getPeerCertificates() {
            return null;
        }
    }

    private class CachedSocket extends CachedBodyEmitter implements AsyncSocket {
        boolean closed;
        boolean open;
        CompletedCallback closedCallback;
        public CachedSocket(EntryCacheResponse cacheResponse, long contentLength) {
            super(cacheResponse, contentLength);
            allowEnd = true;
        }

        @Override
        public void end() {
        }

        @Override
        protected void report(Exception e) {
            super.report(e);
            if (closed)
                return;
            closed = true;
            if (closedCallback != null)
                closedCallback.onCompleted(e);
        }

        @Override
        public void write(ByteBufferList bb) {
            // it's gonna write headers and stuff... whatever
            bb.recycle();
        }

        @Override
        public WritableCallback getWriteableCallback() {
            return null;
        }

        @Override
        public void setWriteableCallback(WritableCallback handler) {
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public CompletedCallback getClosedCallback() {
            return closedCallback;
        }

        @Override
        public void setClosedCallback(CompletedCallback handler) {
            closedCallback = handler;
        }

        @Override
        public AsyncServer getServer() {
            return server;
        }
    }

    class EntryEditor {
        String key;
        File[] temps;
        FileOutputStream[] outs;
        boolean done;
        public EntryEditor(String key) {
            this.key = key;
            temps = cache.getTempFiles(ENTRY_COUNT);
            outs = new FileOutputStream[ENTRY_COUNT];
        }

        void commit() {
            StreamUtility.closeQuietly(outs);
            if (done)
                return;
            cache.commitTempFiles(key, temps);
            writeSuccessCount++;
            done = true;
        }

        FileOutputStream newOutputStream(int index) throws IOException {
            if (outs[index] == null)
                outs[index] = new FileOutputStream(temps[index]);
            return outs[index];
        }

        void abort() {
            StreamUtility.closeQuietly(outs);
            FileCache.removeFiles(temps);
            if (done)
                return;
            writeAbortCount++;
            done = true;
        }
    }
}
