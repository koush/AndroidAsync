package com.koushikdutta.async.http;

import android.os.Handler;

import com.koushikdutta.async.AsyncSSLException;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware.OnRequestCompleteData;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.koushikdutta.async.http.callback.RequestCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.parser.AsyncParser;
import com.koushikdutta.async.parser.ByteBufferListParser;
import com.koushikdutta.async.parser.JSONObjectParser;
import com.koushikdutta.async.parser.StringParser;
import com.koushikdutta.async.stream.OutputStreamDataCallback;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class AsyncHttpClient {
    private static AsyncHttpClient mDefaultInstance;
    public static AsyncHttpClient getDefaultInstance() {
        if (mDefaultInstance == null)
            mDefaultInstance = new AsyncHttpClient(AsyncServer.getDefault());

        return mDefaultInstance;
    }

    final ArrayList<AsyncHttpClientMiddleware> mMiddleware = new ArrayList<AsyncHttpClientMiddleware>();
    public ArrayList<AsyncHttpClientMiddleware> getMiddleware() {
        return mMiddleware;
    }
    public void insertMiddleware(AsyncHttpClientMiddleware middleware) {
        mMiddleware.add(0, middleware);
    }

    AsyncSSLSocketMiddleware sslSocketMiddleware;
    AsyncSocketMiddleware socketMiddleware;
    AsyncServer mServer;
    public AsyncHttpClient(AsyncServer server) {
        mServer = server;
        insertMiddleware(socketMiddleware = new AsyncSocketMiddleware(this));
        insertMiddleware(sslSocketMiddleware = new AsyncSSLSocketMiddleware(this));
    }

    public AsyncSocketMiddleware getSocketMiddleware() {
        return socketMiddleware;
    }

    public AsyncSSLSocketMiddleware getSSLSocketMiddleware() {
        return sslSocketMiddleware;
    }

    public Future<AsyncHttpResponse> execute(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        FutureAsyncHttpResponse ret;
        execute(request, 0, ret = new FutureAsyncHttpResponse(), callback);
        return ret;
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

            if (socket != null)
                socket.close();

            if (scheduled != null)
                mServer.removeAllCallbacks(scheduled);

            return true;
        }
    }

    private void reportConnectedCompleted(FutureAsyncHttpResponse cancel, Exception ex, AsyncHttpResponseImpl response, AsyncHttpRequest request, final HttpConnectCallback callback) {
        assert callback != null;
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
            assert ex != null || response.getSocket() == null || response.getDataCallback() != null;
            return;
        }

        if (response != null) {
            // the request was cancelled, so close up shop, and eat any pending data
            response.setDataCallback(new NullDataCallback());
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

    private void executeAffinity(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback) {
        assert mServer.isAffinityThread();
        if (redirectCount > 15) {
            reportConnectedCompleted(cancel, new Exception("too many redirects"), null, request, callback);
            return;
        }
        final URI uri = request.getUri();
        final OnRequestCompleteData data = new OnRequestCompleteData();
        request.executionTime = System.currentTimeMillis();
        data.request = request;

        request.logd("Executing request.");

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
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (cancel.isCancelled()) {
                    if (socket != null)
                        socket.close();
                    return;
                }

                // 3) on connect, cancel timeout
                if (cancel.timeoutRunnable != null)
                    mServer.removeAllCallbacks(cancel.scheduled);

                data.socket = socket;
                synchronized (mMiddleware) {
                    for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                        middleware.onSocket(data);
                    }
                }

                cancel.socket = socket;

                if (ex != null) {
                    reportConnectedCompleted(cancel, ex, null, request, callback);
                    return;
                }

                // 4) wait for request to be sent fully
                // and
                // 6) wait for headers
                final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
                    @Override
                    protected void onRequestCompleted(Exception ex) {
                        if (cancel.isCancelled())
                            return;
                        // 5) after request is sent, set a header timeout
                        if (cancel.timeoutRunnable != null && data.headers == null) {
                            mServer.removeAllCallbacks(cancel.scheduled);
                            cancel.scheduled = mServer.postDelayed(cancel.timeoutRunnable, getTimeoutRemaining(request));
                        }
                    }

                    @Override
                    public void setDataEmitter(DataEmitter emitter) {
                        data.bodyEmitter = emitter;
                        synchronized (mMiddleware) {
                            for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                                middleware.onBodyDecoder(data);
                            }
                        }
                        mHeaders = data.headers;

                        super.setDataEmitter(data.bodyEmitter);

                        RawHeaders headers = mHeaders.getHeaders();
                        if ((headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) && request.getFollowRedirect()) {
                            URI redirect = URI.create(headers.get("Location"));
                            if (redirect == null || redirect.getScheme() == null) {
                                redirect = URI.create(uri.toString().substring(0, uri.toString().length() - uri.getPath().length()) + headers.get("Location"));
                            }
                            AsyncHttpRequest newReq = new AsyncHttpRequest(redirect, request.getMethod());
                            newReq.executionTime = request.executionTime;
                            newReq.logLevel = request.logLevel;
                            newReq.LOGTAG = request.LOGTAG;
                            request.logi("Redirecting");
                            newReq.logi("Redirected");
                            execute(newReq, redirectCount + 1, cancel, callback);

                            setDataCallback(new NullDataCallback());
                            return;
                        }

                        request.logv("Final (post cache response) headers: " + mHeaders.getHeaders().toHeaderString());

                        // at this point the headers are done being modified
                        reportConnectedCompleted(cancel, null, this, request, callback);
                    }

                    protected void onHeadersReceived() {
                        try {
                            if (cancel.isCancelled())
                                return;

                            // 7) on headers, cancel timeout
                            if (cancel.timeoutRunnable != null)
                                mServer.removeAllCallbacks(cancel.scheduled);

                            // allow the middleware to massage the headers before the body is decoded
                            request.logv("Received headers: " + mHeaders.getHeaders().toHeaderString());

                            data.headers = mHeaders;
                            synchronized (mMiddleware) {
                                for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                                    middleware.onHeadersReceived(data);
                                }
                            }
                            mHeaders = data.headers;

                            // drop through, and setDataEmitter will be called for the body decoder.
                            // headers will be further massaged in there.
                        }
                        catch (Exception ex) {
                            reportConnectedCompleted(cancel, ex, null, request, callback);
                        }
                    }

                    @Override
                    protected void report(Exception ex) {
                        if (cancel.isCancelled())
                            return;
                        if (ex instanceof AsyncSSLException) {
                            request.loge("SSL Exception", ex);
                            AsyncSSLException ase = (AsyncSSLException)ex;
                            request.onHandshakeException(ase);
                            if (ase.getIgnore())
                                return;
                        }
                        final AsyncSocket socket = getSocket();
                        if (socket == null)
                            return;
                        super.report(ex);
                        if (!socket.isOpen() || ex != null) {
                            if (getHeaders() == null && ex != null)
                                reportConnectedCompleted(cancel, ex, null, request, callback);
                        }

                        data.exception = ex;
                        synchronized (mMiddleware) {
                            for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                                middleware.onRequestComplete(data);
                            }
                        }
                    }


                    @Override
                    public AsyncSocket detachSocket() {
                        request.logd("Detaching socket");
                        AsyncSocket socket = getSocket();
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

                ret.setSocket(socket);
            }
        };

        synchronized (mMiddleware) {
            for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                Cancellable socketCancellable = middleware.getSocket(data);
                if (socketCancellable != null) {
                    data.socketCancellable = socketCancellable;
                    cancel.setParent(socketCancellable);
                    return;
                }
            }
        }
        assert false;
    }

    public Future<AsyncHttpResponse> execute(URI uri, final HttpConnectCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }

    public Future<AsyncHttpResponse> execute(String uri, final HttpConnectCallback callback) {
        return execute(new AsyncHttpGet(URI.create(uri)), callback);
    }

    public static abstract class RequestCallbackBase<T> implements RequestCallback<T> {
        @Override
        public void onProgress(AsyncHttpResponse response, int downloaded, int total) {
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

    public static abstract class FileCallback extends RequestCallbackBase<File> {
    }

    @Deprecated
    public Future<ByteBufferList> get(String uri, DownloadCallback callback) {
        return getByteBufferList(uri, callback);
    }

    public Future<ByteBufferList> getByteBufferList(String uri) {
        return getByteBufferList(uri, null);
    }
    public Future<ByteBufferList> getByteBufferList(String uri, DownloadCallback callback) {
        return executeByteBufferList(new AsyncHttpGet(uri), callback);
    }

    public Future<ByteBufferList> executeByteBufferList(AsyncHttpRequest request, DownloadCallback callback) {
        return execute(request, new ByteBufferListParser(), callback);
    }

    @Deprecated
    public Future<String> get(String uri, final StringCallback callback) {
        return executeString(new AsyncHttpGet(uri), callback);
    }
    @Deprecated
    public Future<String> execute(AsyncHttpRequest req, final StringCallback callback) {
        return executeString(req, callback);
    }

    public Future<String> getString(String uri) {
        return executeString(new AsyncHttpGet(uri), null);
    }
    public Future<String> getString(String uri, final StringCallback callback) {
        return executeString(new AsyncHttpGet(uri), callback);
    }

    public Future<String> executeString(AsyncHttpRequest req) {
        return executeString(req, null);
    }
    public Future<String> executeString(AsyncHttpRequest req, final StringCallback callback) {
        return execute(req, new StringParser(), callback);
    }

    @Deprecated
    public Future<JSONObject> get(String uri, final JSONObjectCallback callback) {
        return executeJSONObject(new AsyncHttpGet(uri), callback);
    }
    @Deprecated
    public Future<JSONObject> execute(AsyncHttpRequest req, final JSONObjectCallback callback) {
        return executeJSONObject(req, callback);
    }

    public Future<JSONObject> getJSONObject(String uri) {
        return getJSONObject(uri, null);
    }
    public Future<JSONObject> getJSONObject(String uri, final JSONObjectCallback callback) {
        return executeJSONObject(new AsyncHttpGet(uri), callback);
    }

    public Future<JSONObject> executeJSONObject(AsyncHttpRequest req) {
        return executeJSONObject(req, null);
    }
    public Future<JSONObject> executeJSONObject(AsyncHttpRequest req, final JSONObjectCallback callback) {
        return execute(req, new JSONObjectParser(), callback);
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

    private <T> void invoke(Handler handler, final RequestCallback<T> callback, final SimpleFuture<T> future, final AsyncHttpResponse response, final Exception e, final T result) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                invokeWithAffinity(callback, future, response, e, result);
            }
        };
        if (handler == null)
            mServer.post(runnable);
        else
            AsyncServer.post(handler, runnable);
    }

    private void invokeProgress(final RequestCallback callback, final AsyncHttpResponse response, final int downloaded, final int total) {
        if (callback != null)
            callback.onProgress(response, downloaded, total);
    }

    private void invokeConnect(final RequestCallback callback, final AsyncHttpResponse response) {
        if (callback != null)
            callback.onConnect(response);
    }

    @Deprecated
    public Future<File> get(String uri, final String filename, final FileCallback callback) {
        return executeFile(new AsyncHttpGet(uri), filename, callback);
    }
    @Deprecated
    public Future<File> execute(AsyncHttpRequest req, final String filename, final FileCallback callback) {
        return executeFile(req, filename, callback);
    }

    public Future<File> getFile(String uri, final String filename) {
        return getFile(uri, filename, null);
    }
    public Future<File> getFile(String uri, final String filename, final FileCallback callback) {
        return executeFile(new AsyncHttpGet(uri), filename, callback);
    }

    public Future<File> executeFile(AsyncHttpRequest req, final String filename) {
        return executeFile(req, filename, null);
    }
    public Future<File> executeFile(AsyncHttpRequest req, final String filename, final FileCallback callback) {
        final Handler handler = req.getHandler();
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
                    cancel.get().setDataCallback(new NullDataCallback());
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
            int mDownloaded = 0;

            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    try {
                        fout.close();
                    }
                    catch (IOException e) {
                    }
                    file.delete();
                    invoke(handler, callback, ret, response, ex, null);
                    return;
                }
                invokeConnect(callback, response);

                final int contentLength = response.getHeaders().getContentLength();

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
                            invoke(handler, callback, ret, response, ex, null);
                        }
                        else {
                            invoke(handler, callback, ret, response, null, file);
                        }
                    }
                });
            }
        });
        return ret;
    }

    private <T> SimpleFuture<T> execute(AsyncHttpRequest req, final AsyncParser<T> parser, final RequestCallback<T> callback) {
        final FutureAsyncHttpResponse cancel = new FutureAsyncHttpResponse();
        final SimpleFuture<T> ret = new SimpleFuture<T>();
        final Handler handler = req.getHandler();
        execute(req, 0, cancel, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    invoke(handler, callback, ret, response, ex, null);
                    return;
                }
                invokeConnect(callback, response);

                final int contentLength = response.getHeaders().getContentLength();

                Future<T> parsed = parser.parse(response)
                .setCallback(new FutureCallback<T>() {
                    @Override
                    public void onCompleted(Exception e, T result) {
                        invoke(handler, callback, ret, response, e, result);
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
                WebSocket ws = WebSocketImpl.finishHandshake(req.getHeaders().getHeaders(), response);
                if (ws == null) {
                    if (!ret.setComplete(new Exception("Unable to complete websocket handshake")))
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
        assert callback != null;
        final AsyncHttpGet get = new AsyncHttpGet(uri.replace("ws://", "http://").replace("wss://", "https://"));
        return websocket(get, protocol, callback);
    }

    public AsyncServer getServer() {
        return mServer;
    }
}
