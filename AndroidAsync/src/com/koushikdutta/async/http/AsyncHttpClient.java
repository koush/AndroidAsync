package com.koushikdutta.async.http;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import com.koushikdutta.async.AsyncSSLException;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.koushikdutta.async.http.callback.RequestCallback;
import com.koushikdutta.async.http.spdy.SpdyMiddleware;
import com.koushikdutta.async.parser.AsyncParser;
import com.koushikdutta.async.parser.ByteBufferListParser;
import com.koushikdutta.async.parser.JSONArrayParser;
import com.koushikdutta.async.parser.JSONObjectParser;
import com.koushikdutta.async.parser.StringParser;
import com.koushikdutta.async.stream.OutputStreamDataCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

public class AsyncHttpClient {
    private static AsyncHttpClient mDefaultInstance;
    public static AsyncHttpClient getDefaultInstance() {
        if (mDefaultInstance == null)
            mDefaultInstance = new AsyncHttpClient(AsyncServer.getDefault());

        return mDefaultInstance;
    }

    final List<AsyncHttpClientMiddleware> mMiddleware = new CopyOnWriteArrayList<>();
    public Collection<AsyncHttpClientMiddleware> getMiddleware() {
        return mMiddleware;
    }
    public void insertMiddleware(AsyncHttpClientMiddleware middleware) {
        mMiddleware.add(0, middleware);
    }

    SpdyMiddleware sslSocketMiddleware;
    AsyncSocketMiddleware socketMiddleware;
    HttpTransportMiddleware httpTransportMiddleware;
    AsyncServer mServer;
    public AsyncHttpClient(AsyncServer server) {
        mServer = server;
        insertMiddleware(socketMiddleware = new AsyncSocketMiddleware(this));
        insertMiddleware(sslSocketMiddleware = new SpdyMiddleware(this));
        insertMiddleware(httpTransportMiddleware = new HttpTransportMiddleware());
        sslSocketMiddleware.addEngineConfigurator(new SSLEngineSNIConfigurator());
    }

    @SuppressLint("NewApi")
    private static void setupAndroidProxy(AsyncHttpRequest request) {
        // using a explicit proxy?
        if (request.proxyHost != null)
            return;

        List<Proxy> proxies;
        try {
            proxies = ProxySelector.getDefault().select(URI.create(request.getUri().toString()));
        }
        catch (Exception e) {
            // uri parsing craps itself sometimes.
            return;
        }
        if (proxies.isEmpty())
            return;
        Proxy proxy = proxies.get(0);
        if (proxy.type() != Proxy.Type.HTTP)
            return;
        if (!(proxy.address() instanceof InetSocketAddress))
            return;
        InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
        String proxyHost;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            proxyHost = proxyAddress.getHostString();
        }
        else {
            InetAddress address = proxyAddress.getAddress();
            if (address!=null)
                proxyHost = address.getHostAddress();
            else
                proxyHost = proxyAddress.getHostName();
        }
        request.enableProxy(proxyHost, proxyAddress.getPort());
    }

    public AsyncSocketMiddleware getSocketMiddleware() {
        return socketMiddleware;
    }

    public SpdyMiddleware getSSLSocketMiddleware() {
        return sslSocketMiddleware;
    }

    public Future<AsyncHttpResponse> execute(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        FutureAsyncHttpResponse ret;
        execute(request, 0, ret = new FutureAsyncHttpResponse(), callback);
        return ret;
    }

    public Future<AsyncHttpResponse> execute(String uri, final HttpConnectCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }

    private static final String LOGTAG = "AsyncHttp";
    private class FutureAsyncHttpResponse extends SimpleFuture<AsyncHttpResponse> {
        public AsyncSocket socket;
        public Object scheduled;
        public Runnable timeoutRunnable;

        @Override
        public boolean cancel() {
            if (!super.cancel())
                return false;

            if (socket != null) {
                socket.setDataCallback(new DataCallback.NullDataCallback());
                socket.close();
            }

            if (scheduled != null)
                mServer.removeAllCallbacks(scheduled);

            return true;
        }
    }

    private void reportConnectedCompleted(FutureAsyncHttpResponse cancel, Exception ex, AsyncHttpResponseImpl response, AsyncHttpRequest request, final HttpConnectCallback callback) {
        assert callback != null;
        mServer.removeAllCallbacks(cancel.scheduled);
        boolean complete;
        if (ex != null) {
            request.loge("Connection error", ex);
            complete = cancel.setComplete(ex);
        }
        else {
            request.logd("Connection successful");
            complete = cancel.setComplete(response);
        }
        if (complete) {
            callback.onConnectCompleted(ex, response);
            assert ex != null || response.socket() == null || response.getDataCallback() != null || response.isPaused();
            return;
        }

        if (response != null) {
            // the request was cancelled, so close up shop, and eat any pending data
            response.setDataCallback(new DataCallback.NullDataCallback());
            response.close();
        }
    }

    private void execute(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback) {
        if (mServer.isAffinityThread()) {
            executeAffinity(request, redirectCount, cancel, callback);
        }
        else {
            mServer.post(new Runnable() {
                @Override
                public void run() {
                    executeAffinity(request, redirectCount, cancel, callback);
                }
            });
        }
    }

    private static long getTimeoutRemaining(AsyncHttpRequest request) {
        // need a better way to calculate this.
        // a timer of sorts that stops/resumes.
        return request.getTimeout();
    }

    private static void copyHeader(AsyncHttpRequest from, AsyncHttpRequest to, String header) {
        String value = from.getHeaders().get(header);
        if (!TextUtils.isEmpty(value))
            to.getHeaders().set(header, value);
    }

    private void executeAffinity(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback) {
        assert mServer.isAffinityThread();
        if (redirectCount > 15) {
            reportConnectedCompleted(cancel, new RedirectLimitExceededException("too many redirects"), null, request, callback);
            return;
        }
        final Uri uri = request.getUri();
        final AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data = new AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData();
        request.executionTime = System.currentTimeMillis();
        data.request = request;

        request.logd("Executing request.");

        for (AsyncHttpClientMiddleware middleware: mMiddleware) {
            middleware.onRequest(data);
        }

        // flow:
        // 1) set a connect timeout
        // 2) wait for connect
        // 3) on connect, cancel timeout
        // 4) wait for request to be sent fully
        // 5) after request is sent, set a header timeout
        // 6) wait for headers
        // 7) on headers, cancel timeout
        // 8) TODO: response can take as long as it wants to arrive?

        if (request.getTimeout() > 0) {
            // set connect timeout
            cancel.timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    // we've timed out, kill the connections
                    if (data.socketCancellable != null) {
                        data.socketCancellable.cancel();
                        if (data.socket != null)
                            data.socket.close();
                    }
                    reportConnectedCompleted(cancel, new TimeoutException(), null, request, callback);
                }
            };
            cancel.scheduled = mServer.postDelayed(cancel.timeoutRunnable, getTimeoutRemaining(request));
        }

        // 2) wait for a connect
        data.connectCallback = new ConnectCallback() {
            boolean reported;
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (reported) {
                    if (socket != null) {
                        socket.setDataCallback(new DataCallback.NullDataCallback());
                        socket.setEndCallback(new CompletedCallback.NullCompletedCallback());
                        socket.close();
                        throw new AssertionError("double connect callback");
                    }
                }
                reported = true;

                request.logv("socket connected");
                if (cancel.isCancelled()) {
                    if (socket != null)
                        socket.close();
                    return;
                }

                // 3) on connect, cancel timeout
                if (cancel.timeoutRunnable != null)
                    mServer.removeAllCallbacks(cancel.scheduled);

                if (ex != null) {
                    reportConnectedCompleted(cancel, ex, null, request, callback);
                    return;
                }

                data.socket = socket;
                cancel.socket = socket;

                executeSocket(request, redirectCount, cancel, callback, data);
            }
        };

        // set up the system default proxy and connect
        setupAndroidProxy(request);

        // set the implicit content type
        if (request.getBody() != null) {
            if (request.getHeaders().get("Content-Type") == null)
                request.getHeaders().set("Content-Type", request.getBody().getContentType());
        }

        final Exception unsupportedURI;
        for (AsyncHttpClientMiddleware middleware: mMiddleware) {
            Cancellable socketCancellable = middleware.getSocket(data);
            if (socketCancellable != null) {
                data.socketCancellable = socketCancellable;
                cancel.setParent(socketCancellable);
                return;
            }
        }
        unsupportedURI = new IllegalArgumentException("invalid uri="+request.getUri()+" middlewares="+mMiddleware);
        reportConnectedCompleted(cancel, unsupportedURI, null, request, callback);
    }

    private void executeSocket(final AsyncHttpRequest request, final int redirectCount,
                               final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback,
                               final AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data) {
        // 4) wait for request to be sent fully
        // and
        // 6) wait for headers
        final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
            @Override
            protected void onRequestCompleted(Exception ex) {
                if (ex != null) {
                    reportConnectedCompleted(cancel, ex, null, request, callback);
                    return;
                }

                request.logv("request completed");
                if (cancel.isCancelled())
                    return;
                // 5) after request is sent, set a header timeout
                if (cancel.timeoutRunnable != null && mHeaders == null) {
                    mServer.removeAllCallbacks(cancel.scheduled);
                    cancel.scheduled = mServer.postDelayed(cancel.timeoutRunnable, getTimeoutRemaining(request));
                }

                for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                    middleware.onRequestSent(data);
                }
            }

            @Override
            public void setDataEmitter(DataEmitter emitter) {
                data.bodyEmitter = emitter;
                for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                    middleware.onBodyDecoder(data);
                }

                super.setDataEmitter(data.bodyEmitter);

                Headers headers = mHeaders;
                int responseCode = code();
                if ((responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == 307) && request.getFollowRedirect()) {
                    String location = headers.get("Location");
                    Uri redirect;
                    try {
                        redirect = Uri.parse(location);
                        if (redirect.getScheme() == null) {
                            redirect = Uri.parse(new URL(new URL(request.getUri().toString()), location).toString());
                        }
                    }
                    catch (Exception e) {
                        reportConnectedCompleted(cancel, e, this, request, callback);
                        return;
                    }
                    final String method = request.getMethod().equals(AsyncHttpHead.METHOD) ? AsyncHttpHead.METHOD : AsyncHttpGet.METHOD;
                    AsyncHttpRequest newReq = new AsyncHttpRequest(redirect, method);
                    newReq.executionTime = request.executionTime;
                    newReq.logLevel = request.logLevel;
                    newReq.LOGTAG = request.LOGTAG;
                    newReq.proxyHost = request.proxyHost;
                    newReq.proxyPort = request.proxyPort;
                    setupAndroidProxy(newReq);
                    copyHeader(request, newReq, "User-Agent");
                    copyHeader(request, newReq, "Range");
                    request.logi("Redirecting");
                    newReq.logi("Redirected");
                    execute(newReq, redirectCount + 1, cancel, callback);

                    setDataCallback(new NullDataCallback());
                    return;
                }

                request.logv("Final (post cache response) headers:\n" + toString());

                // at this point the headers are done being modified
                reportConnectedCompleted(cancel, null, this, request, callback);
            }

            protected void onHeadersReceived() {
                super.onHeadersReceived();
                if (cancel.isCancelled())
                    return;

                // 7) on headers, cancel timeout
                if (cancel.timeoutRunnable != null)
                    mServer.removeAllCallbacks(cancel.scheduled);

                // allow the middleware to massage the headers before the body is decoded
                request.logv("Received headers:\n" + toString());

                for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                    middleware.onHeadersReceived(data);
                }

                // drop through, and setDataEmitter will be called for the body decoder.
                // headers will be further massaged in there.
            }

            @Override
            protected void report(Exception ex) {
                if (ex != null)
                    request.loge("exception during response", ex);
                if (cancel.isCancelled())
                    return;
                if (ex instanceof AsyncSSLException) {
                    request.loge("SSL Exception", ex);
                    AsyncSSLException ase = (AsyncSSLException)ex;
                    request.onHandshakeException(ase);
                    if (ase.getIgnore())
                        return;
                }
                final AsyncSocket socket = socket();
                if (socket == null)
                    return;
                super.report(ex);
                if (!socket.isOpen() || ex != null) {
                    if (headers() == null && ex != null)
                        reportConnectedCompleted(cancel, ex, null, request, callback);
                }

                data.exception = ex;
                for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                    middleware.onResponseComplete(data);
                }
            }

            @Override
            public AsyncSocket detachSocket() {
                request.logd("Detaching socket");
                AsyncSocket socket = socket();
                if (socket == null)
                    return null;
                socket.setWriteableCallback(null);
                socket.setClosedCallback(null);
                socket.setEndCallback(null);
                socket.setDataCallback(null);
                setSocket(null);
                return socket;
            }
        };

        data.sendHeadersCallback = new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null)
                    ret.report(ex);
                else
                    ret.onHeadersSent();
            }
        };
        data.receiveHeadersCallback = new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null)
                    ret.report(ex);
                else
                    ret.onHeadersReceived();
            }
        };
        data.response = ret;
        ret.setSocket(data.socket);

        for (AsyncHttpClientMiddleware middleware : mMiddleware) {
            if (middleware.exchangeHeaders(data))
                break;
        }
    }

    public static abstract class RequestCallbackBase<T> implements RequestCallback<T> {
        @Override
        public void onProgress(AsyncHttpResponse response, long downloaded, long total) {
        }
        @Override
        public void onConnect(AsyncHttpResponse response) {
        }
    }

    public static abstract class DownloadCallback extends RequestCallbackBase<ByteBufferList> {
    }

    public static abstract class StringCallback extends RequestCallbackBase<String> {
    }

    public static abstract class JSONObjectCallback extends RequestCallbackBase<JSONObject> {
    }
    
    public static abstract class JSONArrayCallback extends RequestCallbackBase<JSONArray> {
    }

    public static abstract class FileCallback extends RequestCallbackBase<File> {
    }

    public Future<ByteBufferList> executeByteBufferList(AsyncHttpRequest request, DownloadCallback callback) {
        return execute(request, new ByteBufferListParser(), callback);
    }

    public Future<String> executeString(AsyncHttpRequest req, final StringCallback callback) {
        return execute(req, new StringParser(), callback);
    }

    public Future<JSONObject> executeJSONObject(AsyncHttpRequest req, final JSONObjectCallback callback) {
        return execute(req, new JSONObjectParser(), callback);
    }

    public Future<JSONArray> executeJSONArray(AsyncHttpRequest req, final JSONArrayCallback callback) {
        return execute(req, new JSONArrayParser(), callback);
    }

    private <T> void invokeWithAffinity(final RequestCallback<T> callback, SimpleFuture<T> future, final AsyncHttpResponse response, final Exception e, final T result) {
        boolean complete;
        if (e != null)
            complete = future.setComplete(e);
        else
            complete = future.setComplete(result);
        if (!complete)
            return;
        if (callback != null)
            callback.onCompleted(e, response, result);
    }

    private <T> void invoke(final RequestCallback<T> callback, final SimpleFuture<T> future, final AsyncHttpResponse response, final Exception e, final T result) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                invokeWithAffinity(callback, future, response, e, result);
            }
        };
        mServer.post(runnable);
    }

    private void invokeProgress(final RequestCallback callback, final AsyncHttpResponse response, final long downloaded, final long total) {
        if (callback != null)
            callback.onProgress(response, downloaded, total);
    }

    private void invokeConnect(final RequestCallback callback, final AsyncHttpResponse response) {
        if (callback != null)
            callback.onConnect(response);
    }

    public Future<File> executeFile(AsyncHttpRequest req, final String filename, final FileCallback callback) {
        final File file = new File(filename);
        file.getParentFile().mkdirs();
        final OutputStream fout;
        try {
            fout = new BufferedOutputStream(new FileOutputStream(file), 8192);
        }
        catch (FileNotFoundException e) {
            SimpleFuture<File> ret = new SimpleFuture<File>();
            ret.setComplete(e);
            return ret;
        }
        final FutureAsyncHttpResponse cancel = new FutureAsyncHttpResponse();
        final SimpleFuture<File> ret = new SimpleFuture<File>() {
            @Override
            public void cancelCleanup() {
                try {
                    cancel.get().setDataCallback(new DataCallback.NullDataCallback());
                    cancel.get().close();
                }
                catch (Exception e) {
                }
                try {
                    fout.close();
                }
                catch (Exception e) {
                }
                file.delete();
            }
        };
        ret.setParent(cancel);
        execute(req, 0, cancel, new HttpConnectCallback() {
            long mDownloaded = 0;

            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    try {
                        fout.close();
                    }
                    catch (IOException e) {
                    }
                    file.delete();
                    invoke(callback, ret, response, ex, null);
                    return;
                }
                invokeConnect(callback, response);

                final long contentLength = HttpUtil.contentLength(response.headers());

                response.setDataCallback(new OutputStreamDataCallback(fout) {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        mDownloaded += bb.remaining();
                        super.onDataAvailable(emitter, bb);
                        invokeProgress(callback, response, mDownloaded, contentLength);
                    }
                });
                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        try {
                            fout.close();
                        }
                        catch (IOException e) {
                            ex = e;
                        }
                        if (ex != null) {
                            file.delete();
                            invoke(callback, ret, response, ex, null);
                        }
                        else {
                            invoke(callback, ret, response, null, file);
                        }
                    }
                });
            }
        });
        return ret;
    }

    public <T> SimpleFuture<T> execute(AsyncHttpRequest req, final AsyncParser<T> parser, final RequestCallback<T> callback) {
        final FutureAsyncHttpResponse cancel = new FutureAsyncHttpResponse();
        final SimpleFuture<T> ret = new SimpleFuture<T>();
        execute(req, 0, cancel, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    invoke(callback, ret, response, ex, null);
                    return;
                }
                invokeConnect(callback, response);

                Future<T> parsed = parser.parse(response)
                .setCallback(new FutureCallback<T>() {
                    @Override
                    public void onCompleted(Exception e, T result) {
                        invoke(callback, ret, response, e, result);
                    }
                });

                // reparent to the new parser future
                ret.setParent(parsed);
            }
        });
        ret.setParent(cancel);
        return ret;
    }

    public static interface WebSocketConnectCallback {
        public void onCompleted(Exception ex, WebSocket webSocket);
    }

    public Future<WebSocket> websocket(final AsyncHttpRequest req, String protocol, final WebSocketConnectCallback callback) {
        WebSocketImpl.addWebSocketUpgradeHeaders(req, protocol);
        final SimpleFuture<WebSocket> ret = new SimpleFuture<WebSocket>();
        Cancellable connect = execute(req, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    if (ret.setComplete(ex)) {
                        if (callback != null)
                            callback.onCompleted(ex, null);
                    }
                    return;
                }
                WebSocket ws = WebSocketImpl.finishHandshake(req.getHeaders(), response);
                if (ws == null) {
                    ex = new WebSocketHandshakeException("Unable to complete websocket handshake");
                    if (!ret.setComplete(ex))
                        return;
                }
                else {
                    if (!ret.setComplete(ws))
                        return;
                }
                if (callback != null)
                    callback.onCompleted(ex, ws);
            }
        });

        ret.setParent(connect);
        return ret;
    }

    public Future<WebSocket> websocket(String uri, String protocol, final WebSocketConnectCallback callback) {
//        assert callback != null;
        final AsyncHttpGet get = new AsyncHttpGet(uri.replace("ws://", "http://").replace("wss://", "https://"));
        return websocket(get, protocol, callback);
    }

    public AsyncServer getServer() {
        return mServer;
    }
}
