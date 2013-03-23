package com.koushikdutta.async.http;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.Cancelable;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataWrapperSocket;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.IAsyncSSLSocket;
import com.koushikdutta.async.SimpleCancelable;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;
import com.koushikdutta.async.http.libcore.ResponseSource;
import com.koushikdutta.async.util.cache.Charsets;
import com.koushikdutta.async.util.cache.DiskLruCache;
import com.koushikdutta.async.util.cache.StrictLineReader;

public class ResponseCacheMiddleware extends SimpleMiddleware {
    private DiskLruCache cache;
    private static final int VERSION = 201105;
    private static final int ENTRY_METADATA = 0;
    private static final int ENTRY_BODY = 1;
    private static final int ENTRY_COUNT = 2;
    private AsyncHttpClient client;

    private ResponseCacheMiddleware() {
    }
    
    public static ResponseCacheMiddleware addCache(AsyncHttpClient client, File cacheDir, long size) throws IOException {
        for (AsyncHttpClientMiddleware middleware: client.getMiddleware()) {
            if (middleware instanceof ResponseCacheMiddleware)
                throw new IOException("Response cache already added to http client");
        }
        ResponseCacheMiddleware ret = new ResponseCacheMiddleware();
        ret.client = client;
        ret.cache = DiskLruCache.open(cacheDir, VERSION, ENTRY_COUNT, size);
        client.insertMiddleware(ret);
        return ret;
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
            throw new AssertionError(e);
        }
    }
    
    private class CachedSSLSocket extends CachedSocket implements IAsyncSSLSocket {
        public CachedSSLSocket(CacheResponse cacheResponse) {
            super(cacheResponse);
        }

        @Override
        public X509Certificate[] getPeerCertificates() {
            return null;
        }
    }

    
    private class CachedSocket implements AsyncSocket {
        CacheResponse cacheResponse;
        public CachedSocket(CacheResponse cacheResponse) {
            this.cacheResponse = cacheResponse;
        }

        @Override
        public void setDataCallback(DataCallback callback) {
            dataCallback = callback;            
        }

        DataCallback dataCallback;
        @Override
        public DataCallback getDataCallback() {
            return dataCallback;
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
        
        void report(Exception e) {
            open = false;
            if (endCallback != null)
                endCallback.onCompleted(e);
            if (closedCallback != null)
                closedCallback.onCompleted(e);
        }
        
        void spewInternal() {
            if (pending.remaining() > 0) {
                com.koushikdutta.async.Util.emitAllData(CachedSocket.this, pending);
                if (pending.remaining() > 0)
                    return;
            }

            // fill pending
            try {
                while (pending.remaining() == 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(8192);
                    int read = cacheResponse.getBody().read(buffer.array());
                    if (read == -1) {
                        report(null);
                        return;
                    }
                    buffer.limit(read);
                    pending.add(buffer);
                    com.koushikdutta.async.Util.emitAllData(CachedSocket.this, pending);
                }
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
        public void setEndCallback(CompletedCallback callback) {
            endCallback = callback;
        }

        CompletedCallback endCallback;
        @Override
        public CompletedCallback getEndCallback() {
            return endCallback;
        }

        @Override
        public void write(ByteBuffer bb) {
            // it's gonna write headers and stuff... whatever
            bb.limit(bb.position());
        }

        @Override
        public void write(ByteBufferList bb) {
            // it's gonna write headers and stuff... whatever
            bb.clear();
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
        CacheResponse candidate;
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
    public Cancelable getSocket(final GetSocketData data) {
        if (cache == null)
            return null;
        
        if (!caching)
            return null;
        if (data.request.getHeaders().isNoCache())
            return null;
//        Log.i(LOGTAG, "getting cache socket: " + request.getUri().toString());

        String key = uriToKey(data.request.getUri());
        DiskLruCache.Snapshot snapshot;
        Entry entry;
        try {
            snapshot = cache.get(key);
            if (snapshot == null) {
//                Log.i(LOGTAG, "snapshot fail");
                return null;
            }
            entry = new Entry(snapshot.getInputStream(ENTRY_METADATA));
        } catch (IOException e) {
            // Give up because the cache cannot be read.
            return null;
        }

        if (!entry.matches(data.request.getUri(), data.request.getMethod(), data.request.getHeaders().getHeaders().toMultimap())) {
            snapshot.close();
            return null;
        }
        
        ResponseSource responseSource = ResponseSource.NETWORK;

        CacheResponse candidate = entry.isHttps() ? new EntrySecureCacheResponse(entry, snapshot) : new EntryCacheResponse(entry, snapshot);

        Map<String, List<String>> responseHeadersMap;
        InputStream cachedResponseBody;
        try {
            responseHeadersMap = candidate.getHeaders();
            cachedResponseBody = candidate.getBody();
        }
        catch (Exception e) {
            return null;
        }
        if (responseHeadersMap == null || cachedResponseBody == null) {
            try {
                cachedResponseBody.close();
            }
            catch (Exception e) {
            }
            return null;
        }

        RawHeaders rawResponseHeaders = RawHeaders.fromMultimap(responseHeadersMap);
        ResponseHeaders cachedResponseHeaders = new ResponseHeaders(data.request.getUri(), rawResponseHeaders);

        
        ////
        if (false) {
            final CachedSocket ss = entry.isHttps() ? new CachedSSLSocket((EntrySecureCacheResponse)candidate) : new CachedSocket((EntryCacheResponse)candidate);

            ss.pending.add(ByteBuffer.wrap(rawResponseHeaders.toHeaderString().getBytes()));
            
            client.getServer().post(new Runnable() {
                @Override
                public void run() {
                    data.connectCallback.onConnectCompleted(null, ss);
                    ss.spewInternal();
                }
            });

            if (true)
                return new SimpleCancelable();
        }
        ///
        
        
        long now = System.currentTimeMillis();
        responseSource = cachedResponseHeaders.chooseResponseSource(now, data.request.getHeaders());
        
        if (responseSource == ResponseSource.CACHE) {
            final CachedSocket socket = entry.isHttps() ? new CachedSSLSocket((EntrySecureCacheResponse)candidate) : new CachedSocket((EntryCacheResponse)candidate);

            client.getServer().post(new Runnable() {
                @Override
                public void run() {
                    data.connectCallback.onConnectCompleted(null, socket);
                    socket.spewInternal();
                }
            });
        }
        else if (responseSource == ResponseSource.CONDITIONAL_CACHE) {
            CacheData cacheData = new CacheData();
            cacheData.cachedResponseHeaders = cachedResponseHeaders;
            cacheData.candidate = candidate;
            data.state.putParcelable("cache-data", cacheData);

            return null;
        }
        else {
            // NETWORK or other
            try {
                cachedResponseBody.close();
            }
            catch (Exception e) {
            }
            return null;
        }
        
        return new SimpleCancelable();
    }

    private static class BodyCacher extends FilteredDataEmitter implements Parcelable {
        CacheRequestImpl cacheRequest;
        ByteBufferList cached;

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
                        for (ByteBuffer b: bb) {
                            outputStream.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
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
                cached.add(bb);
                bb.clear();
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
        CacheResponse cacheResponse;

        void spewInternal() {
            if (pending.remaining() > 0) {
                com.koushikdutta.async.Util.emitAllData(BodySpewer.this, pending);
                if (pending.remaining() > 0)
                    return;
            }

            // fill pending
            try {
                while (pending.remaining() == 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(8192);
                    int read = cacheResponse.getBody().read(buffer.array());
                    if (read == -1) {
                        allowEnd = true;
                        report(null);
                        return;
                    }
                    buffer.limit(read);
                    pending.add(buffer);
                    com.koushikdutta.async.Util.emitAllData(BodySpewer.this, pending);
                }
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
            super.report(e);
        }
    }
    

    // step 3) if this is a conditional cache request, serve it from the cache if necessary
    // otherwise, see if it is cacheable
    @Override
    public void onBodyDecoder(OnBodyData data) {
        CacheData cacheData = data.state.getParcelable("cache-data");
        if (cacheData != null) {
            if (cacheData.cachedResponseHeaders.validate(data.headers)) {
                data.headers = cacheData.cachedResponseHeaders.combine(data.headers);
                data.headers.getHeaders().setStatusLine(cacheData.cachedResponseHeaders.getHeaders().getStatusLine());
                
                BodySpewer bodySpewer = new BodySpewer();
                bodySpewer.cacheResponse = cacheData.candidate;
                bodySpewer.setDataEmitter(data.bodyEmitter);
                data.bodyEmitter = bodySpewer;
                bodySpewer.spew();
                return;
            }
            
            // did not validate, so fall through and cache the response
            data.state.remove("cache-data");
        }
        
        if (!caching)
            return;

        if (!data.headers.isCacheable(data.request.getHeaders()))
            return;

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
        }
        catch (Exception e) {
            // Log.e(LOGTAG, "error", e);
            if (cacher.cacheRequest != null)
                cacher.cacheRequest.abort();
            cacher.cacheRequest = null;
        }
    }

    
    @Override
    public void onRequestComplete(OnRequestCompleteData data) {
//        CachingSocket caching = (CachingSocket)com.koushikdutta.async.Util.getWrappedSocket(data.socket, CachingSocket.class);
//        if (caching == null)
//            return;
        
        BodyCacher cacher = data.state.getParcelable("body-cacher");
        if (cacher == null)
            return;

//        Log.i(LOGTAG, "Cache done: " + ex);
        try {
            if (data.exception != null)
                cacher.abort();
            else
                cacher.commit();
        }
        catch (Exception e) {
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

    static class EntryCacheResponse extends CacheResponse {
        private final Entry entry;
        private final DiskLruCache.Snapshot snapshot;
        private final InputStream in;

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

    static class EntrySecureCacheResponse extends SecureCacheResponse {
        private final Entry entry;
        private final DiskLruCache.Snapshot snapshot;
        private final InputStream in;

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
}
