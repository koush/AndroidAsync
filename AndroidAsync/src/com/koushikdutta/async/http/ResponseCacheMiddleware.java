package com.koushikdutta.async.http;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.SecureCacheResponse;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

import com.koushikdutta.async.*;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.http.libcore.Charsets;
import com.koushikdutta.async.http.libcore.DiskLruCache;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;
import com.koushikdutta.async.http.libcore.ResponseSource;
import com.koushikdutta.async.http.libcore.StrictLineReader;

public class ResponseCacheMiddleware extends SimpleMiddleware {
    private DiskLruCache cache;
    private static final int VERSION = 201105;
    private static final int ENTRY_METADATA = 0;
    private static final int ENTRY_BODY = 1;
    private static final int ENTRY_COUNT = 2;
    private AsyncHttpClient client;

    public static final String SERVED_FROM = "X-Served-From";
    public static final String CONDITIONAL_CACHE = "conditional-cache";
    public static final String CACHE = "cache";

    private ResponseCacheMiddleware() {
    }
    
    long size;
    File cacheDir;
    public static ResponseCacheMiddleware addCache(AsyncHttpClient client, File cacheDir, long size) throws IOException {
        for (AsyncHttpClientMiddleware middleware: client.getMiddleware()) {
            if (middleware instanceof ResponseCacheMiddleware)
                throw new IOException("Response cache already added to http client");
        }
        ResponseCacheMiddleware ret = new ResponseCacheMiddleware();
        ret.size = size;
        ret.client = client;
        ret.cacheDir = cacheDir;
        ret.open();
        client.insertMiddleware(ret);
        return ret;
    }
    
    private void open() throws IOException {
        cache = DiskLruCache.open(cacheDir, VERSION, ENTRY_COUNT, size);
    }
    
    boolean caching = true;
    public void setCaching(boolean caching) {
        this.caching = caching;
    }
    
    public boolean getCaching() {
        return caching;
    }

    private static String uriToKey(URI uri) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] md5bytes = messageDigest.digest(uri.toString().getBytes());
            return new BigInteger(1, md5bytes).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    private class CachedSSLSocket extends CachedSocket implements AsyncSSLSocket {
        public CachedSSLSocket(CacheResponse cacheResponse, long contentLength) {
            super(cacheResponse, contentLength);
        }

        @Override
        public X509Certificate[] getPeerCertificates() {
            return null;
        }
    }

    
    private class CachedSocket extends DataEmitterBase implements AsyncSocket {
        CacheResponse cacheResponse;
        long contentLength;
        public CachedSocket(CacheResponse cacheResponse, long contentLength) {
            this.cacheResponse = cacheResponse;
            this.contentLength = contentLength;
        }

        @Override
        public void end() {
        }

        @Override
        public boolean isChunked() {
            return false;
        }

        boolean paused;
        @Override
        public void pause() {
            paused = true;
        }

        boolean closed;
        @Override
        protected void report(Exception e) {
            super.report(e);
            try {
                cacheResponse.getBody().close();
            }
            catch (Exception ex) {
            }
            if (closed)
                return;
            closed = true;
            if (closedCallback != null)
                closedCallback.onCompleted(e);
        }

        boolean first = true;
        void spewInternal() {
            if (pending.remaining() > 0) {
                com.koushikdutta.async.Util.emitAllData(CachedSocket.this, pending);
                if (pending.remaining() > 0)
                    return;
            }

            // fill pending
            try {
                assert first;
                if (!first)
                    return;
                first = false;
                ByteBuffer buffer = ByteBufferList.obtain((int)contentLength);
                assert buffer.position() == 0;
                DataInputStream din = new DataInputStream(cacheResponse.getBody());
                din.readFully(buffer.array(), buffer.arrayOffset(), (int)contentLength);
                pending.add(buffer);
                com.koushikdutta.async.Util.emitAllData(CachedSocket.this, pending);
                assert din.read() == -1;
                report(null);
            }
            catch (IOException e) {
                report(e);
            }
        }

        ByteBufferList pending = new ByteBufferList();
        void spew() {
            getServer().post(new Runnable() {
                @Override
                public void run() {
                    spewInternal();
                }
            });
        }
        
        @Override
        public void resume() {
            paused = false;
            spew();
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public void write(ByteBuffer bb) {
            // it's gonna write headers and stuff... whatever
            bb.limit(bb.position());
        }

        @Override
        public void write(ByteBufferList bb) {
            // it's gonna write headers and stuff... whatever
            bb.recycle();
        }

        @Override
        public void setWriteableCallback(WritableCallback handler) {
        }

        @Override
        public WritableCallback getWriteableCallback() {
            return null;
        }

        boolean open;
        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void setClosedCallback(CompletedCallback handler) {
            closedCallback = handler;            
        }

        CompletedCallback closedCallback;
        @Override
        public CompletedCallback getClosedCallback() {
            return closedCallback;
        }

        @Override
        public AsyncServer getServer() {
            return client.getServer();
        }
    }
    
    public static class CacheData implements Parcelable {
        DiskLruCache.Snapshot snapshot;
        CacheResponse candidate;
        long contentLength;
        ResponseHeaders cachedResponseHeaders;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }
        
    }
    
    private static final String LOGTAG = "AsyncHttpCache";
    
    // step 1) see if we can serve request from the cache directly.
    // also see if this can be turned into a conditional cache request.
    @Override
    public Cancellable getSocket(final GetSocketData data) {
        if (cache == null || !caching || data.request.getHeaders().isNoCache()) {
            networkCount++;
            return null;
        }

        String key = uriToKey(data.request.getUri());
        DiskLruCache.Snapshot snapshot = null;
        Entry entry;
        try {
            snapshot = cache.get(key);
            if (snapshot == null) {
                networkCount++;
                return null;
            }
            entry = new Entry(snapshot.getInputStream(ENTRY_METADATA));
        } catch (IOException e) {
            // Give up because the cache cannot be read.
            networkCount++;
            return null;
        }

        // verify the entry matches
        if (!entry.matches(data.request.getUri(), data.request.getMethod(), data.request.getHeaders().getHeaders().toMultimap())) {
            networkCount++;
            snapshot.close();
            return null;
        }

        CacheResponse candidate = entry.isHttps() ? new EntrySecureCacheResponse(entry, snapshot) : new EntryCacheResponse(entry, snapshot);

        Map<String, List<String>> responseHeadersMap;
        InputStream cachedResponseBody;
        try {
            responseHeadersMap = candidate.getHeaders();
            cachedResponseBody = candidate.getBody();
        }
        catch (Exception e) {
            networkCount++;
            snapshot.close();
            return null;
        }
        if (responseHeadersMap == null || cachedResponseBody == null) {
            try {
                cachedResponseBody.close();
            }
            catch (Exception e) {
            }
            networkCount++;
            snapshot.close();
            return null;
        }

        RawHeaders rawResponseHeaders = RawHeaders.fromMultimap(responseHeadersMap);
        ResponseHeaders cachedResponseHeaders = new ResponseHeaders(data.request.getUri(), rawResponseHeaders);
        cachedResponseHeaders.setLocalTimestamps(System.currentTimeMillis(), System.currentTimeMillis());

        long now = System.currentTimeMillis();
        ResponseSource responseSource = cachedResponseHeaders.chooseResponseSource(now, data.request.getHeaders());
        long contentLength = snapshot.getLength(ENTRY_BODY);

        if (responseSource == ResponseSource.CACHE) {
            data.request.logi("Response retrieved from cache");
            final CachedSocket socket = entry.isHttps() ? new CachedSSLSocket((EntrySecureCacheResponse)candidate, contentLength) : new CachedSocket((EntryCacheResponse)candidate, contentLength);
            rawResponseHeaders.removeAll("Content-Encoding");
            rawResponseHeaders.removeAll("Transfer-Encoding");
            rawResponseHeaders.set("Content-Length", String.valueOf(contentLength));
            socket.pending.add(ByteBuffer.wrap(rawResponseHeaders.toHeaderString().getBytes()));

            client.getServer().post(new Runnable() {
                @Override
                public void run() {
                    data.connectCallback.onConnectCompleted(null, socket);
                    socket.spewInternal();
                }
            });
            cacheHitCount++;
            return new SimpleCancellable();
        }
        else if (responseSource == ResponseSource.CONDITIONAL_CACHE) {
            data.request.logi("Response may be served from conditional cache");
            CacheData cacheData = new CacheData();
            cacheData.snapshot = snapshot;
            cacheData.contentLength = contentLength;
            cacheData.cachedResponseHeaders = cachedResponseHeaders;
            cacheData.candidate = candidate;
            data.state.putParcelable("cache-data", cacheData);

            return null;
        }
        else {
            data.request.logd("Response can not be served from cache");
            // NETWORK or other
            try {
                cachedResponseBody.close();
            }
            catch (Exception e) {
            }
            networkCount++;
            snapshot.close();
            return null;
        }
    }

    private static class BodyCacher extends FilteredDataEmitter implements Parcelable {
        CacheRequestImpl cacheRequest;
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
                com.koushikdutta.async.Util.emitAllData(this, cached);
                // couldn't emit it all, so just wait for another day...
                if (cached.remaining() > 0)
                    return;
                cached = null;
            }

            // write to cache... any data not consumed needs to be retained for the next callback
            try {
                if (cacheRequest != null) {
                    OutputStream outputStream = cacheRequest.getBody();
                    if (outputStream != null) {
                        int count = bb.size();
                        for (int i = 0; i < count; i++) {
                            ByteBuffer b = bb.remove();
                            outputStream.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
                            bb.add(b);
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
            
            super.onDataAvailable(emitter, bb);
            
            if (cacheRequest != null && bb.remaining() > 0) {
                cached = new ByteBufferList();
                bb.get(cached);
            }
        }
        
        public void abort() {
            if (cacheRequest != null) {
                cacheRequest.abort();
                cacheRequest = null;
            }
        }
        
        public void commit() {
            if (cacheRequest != null) {
                try {
                    cacheRequest.getBody().close();
                }
                catch (Exception e) {
                }
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }
    }
    
    private static class BodySpewer extends FilteredDataEmitter {
        long contentLength;
        public BodySpewer(long contentLength) {
            this.contentLength = contentLength;
        }
        CacheResponse cacheResponse;

        boolean first = true;
        void spewInternal() {
            if (pending.remaining() > 0) {
                com.koushikdutta.async.Util.emitAllData(BodySpewer.this, pending);
                if (pending.remaining() > 0)
                    return;
            }

            // fill pending
            try {
                assert first;
                if (!first)
                    return;
                first = false;
                ByteBuffer buffer = ByteBufferList.obtain((int)contentLength);
                assert buffer.position() == 0;
                DataInputStream din = new DataInputStream(cacheResponse.getBody());
                din.readFully(buffer.array(), buffer.arrayOffset(), (int)contentLength);
                pending.add(buffer);
                com.koushikdutta.async.Util.emitAllData(this, pending);
                assert din.read() == -1;
                allowEnd = true;
                report(null);
            }
            catch (IOException e) {
                allowEnd = true;
                report(e);
            }
        }

        ByteBufferList pending = new ByteBufferList();
        void spew() {
            getServer().post(new Runnable() {
                @Override
                public void run() {
                    spewInternal();
                }
            });
        }
        
        boolean paused;
        @Override
        public void resume() {
            paused = false;
            spew();
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        boolean allowEnd;
        @Override
        protected void report(Exception e) {
            if (!allowEnd)
                return;
            try {
                cacheResponse.getBody().close();
            }
            catch (Exception ex) {
            }
            super.report(e);
        }
    }
    
    private int conditionalCacheHitCount;
    private int cacheHitCount;
    private int networkCount;
    private int cacheStoreCount;
    
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
    

    // step 3) if this is a conditional cache request, serve it from the cache if necessary
    // otherwise, see if it is cacheable
    @Override
    public void onBodyDecoder(OnBodyData data) {
        CachedSocket cached = (CachedSocket) com.koushikdutta.async.Util.getWrappedSocket(data.socket, CachedSocket.class);
        if (cached != null) {
            data.headers.getHeaders().set(SERVED_FROM, CACHE);
            return;
        }

        CacheData cacheData = data.state.getParcelable("cache-data");
        if (cacheData != null) {
            if (cacheData.cachedResponseHeaders.validate(data.headers)) {
                data.request.logi("Serving response from conditional cache");
                data.headers = cacheData.cachedResponseHeaders.combine(data.headers);
                data.headers.getHeaders().setStatusLine(cacheData.cachedResponseHeaders.getHeaders().getStatusLine());

                data.headers.getHeaders().set(SERVED_FROM, CONDITIONAL_CACHE);
                conditionalCacheHitCount++;
                
                BodySpewer bodySpewer = new BodySpewer(cacheData.contentLength);
                bodySpewer.cacheResponse = cacheData.candidate;
                bodySpewer.setDataEmitter(data.bodyEmitter);
                data.bodyEmitter = bodySpewer;
                bodySpewer.spew();
                return;
            }

            // did not validate, so fall through and cache the response
            data.state.remove("cache-data");
            cacheData.snapshot.close();
        }
        
        if (!caching)
            return;

        if (!data.headers.isCacheable(data.request.getHeaders()) || !data.request.getMethod().equals(AsyncHttpGet.METHOD)) {
            /*
             * Don't cache non-GET responses. We're technically allowed to cache
             * HEAD requests and some POST requests, but the complexity of doing
             * so is high and the benefit is low.
             */
            networkCount++;
            data.request.logd("Response is not cacheable");
            return;
        }

        String key = uriToKey(data.request.getUri());
        RawHeaders varyHeaders = data.request.getHeaders().getHeaders().getAll(data.headers.getVaryFields());
        Entry entry = new Entry(data.request.getUri(), varyHeaders, data.request, data.headers);
        DiskLruCache.Editor editor = null;
        BodyCacher cacher = new BodyCacher();
        try {
            editor = cache.edit(key);
            if (editor == null) {
                // Log.i(LOGTAG, "can't cache");
                return;
            }
            entry.writeTo(editor);


            cacher.cacheRequest = new CacheRequestImpl(editor);
            if (cacher.cacheRequest.getBody() == null)
                return;
//            cacher.cacheData = 
            cacher.setDataEmitter(data.bodyEmitter);
            data.bodyEmitter = cacher;
            
            data.state.putParcelable("body-cacher", cacher);
            data.request.logd("Caching response");
            cacheStoreCount++;
        }
        catch (Exception e) {
            // Log.e(LOGTAG, "error", e);
            if (cacher.cacheRequest != null)
                cacher.cacheRequest.abort();
            cacher.cacheRequest = null;
            networkCount++;
        }
    }

    
    @Override
    public void onRequestComplete(OnRequestCompleteData data) {
        CacheData cacheData = data.state.getParcelable("cache-data");
        if (cacheData != null && cacheData.snapshot != null)
            cacheData.snapshot.close();

        CachedSocket cachedSocket = Util.getWrappedSocket(data.socket, CachedSocket.class);
        if (cachedSocket != null)
            ((SnapshotCacheResponse)cachedSocket.cacheResponse).getSnapshot().close();

        BodyCacher cacher = data.state.getParcelable("body-cacher");
        if (cacher != null) {
            try {
                if (data.exception != null)
                    cacher.abort();
                else
                    cacher.commit();
            }
            catch (Exception e) {
            }
        }
    }
    
    
    int writeSuccessCount;
    int writeAbortCount;
    
    private final class CacheRequestImpl extends CacheRequest {
        private final DiskLruCache.Editor editor;
        private OutputStream cacheOut;
        private boolean done;
        private OutputStream body;

        public CacheRequestImpl(final DiskLruCache.Editor editor) throws IOException {
            this.editor = editor;
            this.cacheOut = editor.newOutputStream(ENTRY_BODY);
            this.body = new FilterOutputStream(cacheOut) {
                @Override public void close() throws IOException {
                    synchronized (ResponseCacheMiddleware.this) {
                        if (done) {
                            return;
                        }
                        done = true;
                        writeSuccessCount++;
                    }
                    super.close();
                    editor.commit();
                }

                @Override
                public void write(byte[] buffer, int offset, int length) throws IOException {
                    // Since we don't override "write(int oneByte)", we can write directly to "out"
                    // and avoid the inefficient implementation from the FilterOutputStream.
                    out.write(buffer, offset, length);
                }
            };
        }

        @Override public void abort() {
            synchronized (ResponseCacheMiddleware.this) {
                if (done) {
                    return;
                }
                done = true;
                writeAbortCount++;
            }
            try {
                cacheOut.close();
            }
            catch (IOException e) {
            }
            try {
                editor.abort();
            } catch (IOException ignored) {
            }
        }

        @Override public OutputStream getBody() throws IOException {
            return body;
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
            try {
                StrictLineReader reader = new StrictLineReader(in, Charsets.US_ASCII);
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
                in.close();
            }
        }

        public Entry(URI uri, RawHeaders varyHeaders, AsyncHttpRequest request, ResponseHeaders responseHeaders) {
            this.uri = uri.toString();
            this.varyHeaders = varyHeaders;
            this.requestMethod = request.getMethod();
            this.responseHeaders = responseHeaders.getHeaders();

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

        public void writeTo(DiskLruCache.Editor editor) throws IOException {
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

        public boolean matches(URI uri, String requestMethod,
                Map<String, List<String>> requestHeaders) {
            return this.uri.equals(uri.toString())
                    && this.requestMethod.equals(requestMethod)
                    && new ResponseHeaders(uri, responseHeaders)
                            .varyMatches(varyHeaders.toMultimap(), requestHeaders);
        }
    }

    /**
     * Returns an input stream that reads the body of a snapshot, closing the
     * snapshot when the stream is closed.
     */
    private static InputStream newBodyInputStream(final DiskLruCache.Snapshot snapshot) {
        return new FilterInputStream(snapshot.getInputStream(ENTRY_BODY)) {
            @Override public void close() throws IOException {
                snapshot.close();
                super.close();
            }
        };
    }

    static interface SnapshotCacheResponse {
        public DiskLruCache.Snapshot getSnapshot();
    }

    static class EntryCacheResponse extends CacheResponse implements SnapshotCacheResponse {
        private final Entry entry;
        private final DiskLruCache.Snapshot snapshot;
        private final InputStream in;

        @Override
        public DiskLruCache.Snapshot getSnapshot() {
            return snapshot;
        }

        public EntryCacheResponse(Entry entry, DiskLruCache.Snapshot snapshot) {
            this.entry = entry;
            this.snapshot = snapshot;
            this.in = newBodyInputStream(snapshot);
        }

        @Override public Map<String, List<String>> getHeaders() {
            return entry.responseHeaders.toMultimap();
        }

        @Override public InputStream getBody() {
            return in;
        }
    }

    static class EntrySecureCacheResponse extends SecureCacheResponse implements SnapshotCacheResponse {
        private final Entry entry;
        private final DiskLruCache.Snapshot snapshot;
        private final InputStream in;

        @Override
        public DiskLruCache.Snapshot getSnapshot() {
            return snapshot;
        }


        public EntrySecureCacheResponse(Entry entry, DiskLruCache.Snapshot snapshot) {
            this.entry = entry;
            this.snapshot = snapshot;
            this.in = newBodyInputStream(snapshot);
        }

        @Override public Map<String, List<String>> getHeaders() {
            return entry.responseHeaders.toMultimap();
        }

        @Override public InputStream getBody() {
            return in;
        }

        @Override public String getCipherSuite() {
            return entry.cipherSuite;
        }

        @Override public List<Certificate> getServerCertificateChain()
                throws SSLPeerUnverifiedException {
            if (entry.peerCertificates == null || entry.peerCertificates.length == 0) {
                throw new SSLPeerUnverifiedException(null);
            }
            return Arrays.asList(entry.peerCertificates.clone());
        }

        @Override public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            if (entry.peerCertificates == null || entry.peerCertificates.length == 0) {
                throw new SSLPeerUnverifiedException(null);
            }
            return ((X509Certificate) entry.peerCertificates[0]).getSubjectX500Principal();
        }

        @Override public List<Certificate> getLocalCertificateChain() {
            if (entry.localCertificates == null || entry.localCertificates.length == 0) {
                return null;
            }
            return Arrays.asList(entry.localCertificates.clone());
        }

        @Override public Principal getLocalPrincipal() {
            if (entry.localCertificates == null || entry.localCertificates.length == 0) {
                return null;
            }
            return ((X509Certificate) entry.localCertificates[0]).getSubjectX500Principal();
        }
    }
    
    public void clear() throws IOException {
        if (cache != null) {
            cache.delete();
            open();
        }
    }
}
