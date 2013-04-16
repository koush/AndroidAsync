package com.koushikdutta.async.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;

import com.koushikdutta.async.AsyncSSLException;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.RequestCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.SimpleCancelable;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware.OnRequestCompleteData;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.stream.OutputStreamDataCallback;

public class AsyncHttpClient {
    private static AsyncHttpClient mDefaultInstance;
    public static AsyncHttpClient getDefaultInstance() {
        if (mDefaultInstance == null)
            mDefaultInstance = new AsyncHttpClient(AsyncServer.getDefault());
        
        return mDefaultInstance;
    }
    
    ArrayList<AsyncHttpClientMiddleware> mMiddleware = new ArrayList<AsyncHttpClientMiddleware>();
    public ArrayList<AsyncHttpClientMiddleware> getMiddleware() {
        return mMiddleware;
    }
    public void insertMiddleware(AsyncHttpClientMiddleware middleware) {
        synchronized (mMiddleware) {
            mMiddleware.add(0, middleware);
        }
    }

    AsyncServer mServer;
    public AsyncHttpClient(AsyncServer server) {
        mServer = server;
        insertMiddleware(new AsyncSocketMiddleware(this));
        insertMiddleware(new AsyncSSLSocketMiddleware(this));
    }

    private static abstract class InternalConnectCallback implements ConnectCallback {
        boolean reused = false;
    }

    public Cancellable execute(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        CancelableImpl ret;
        execute(request, callback, 0, ret = new CancelableImpl());
        return ret;
    }
    
    private static final String LOGTAG = "AsyncHttp";
    private static class CancelableImpl extends SimpleCancelable {
        public HttpConnectCallback callback;
        public Cancellable socketCancelable;
        public AsyncSocket socket;
        
        @Override
        public boolean cancel() {
            if (!super.cancel())
                return false;
            
            if (socketCancelable != null) {
                socketCancelable.cancel();
            }

            if (socket != null)
                socket.close();
            
            // call this?
            callback.onConnectCompleted(new CancellationException(), null);
            
            return true;
        }
    }
    
    private void reportConnectedCompleted(CancelableImpl cancel, Exception ex, AsyncHttpResponseImpl response, final HttpConnectCallback callback) {
        if (cancel.setComplete())
            callback.onConnectCompleted(ex, response);
    }

    private void execute(final AsyncHttpRequest request, final HttpConnectCallback callback, final int redirectCount, final CancelableImpl cancel) {
        if (redirectCount > 5) {
            reportConnectedCompleted(cancel, new Exception("too many redirects"), null, callback);
            return;
        }
        final URI uri = request.getUri();
        final OnRequestCompleteData data = new OnRequestCompleteData();
        data.request = request;

        final InternalConnectCallback socketConnected = new InternalConnectCallback() {
            Object scheduled;
            {
                if (request.getTimeout() > 0) {
                    scheduled = mServer.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            cancel.cancel();
                            reportConnectedCompleted(cancel, new TimeoutException(), null, callback);
                        }
                    }, request.getTimeout());
                }
                else {
                    scheduled = null;
                }
            }
            
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket _socket) {
                if (cancel.isCancelled()) {
                    if (_socket != null)
                        _socket.close();
                    return;
                }

                data.socket = _socket;
                for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                    middleware.onSocket(data);
                }
                
                AsyncSocket socket = data.socket;
                cancel.socket = socket;

                if (ex != null) {
                    reportConnectedCompleted(cancel, ex, null, callback);
                    return;
                }

                final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
                    @Override
                    public void setDataEmitter(DataEmitter emitter) {
                        data.bodyEmitter = emitter;
                        for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                            middleware.onBodyDecoder(data);
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
                            execute(newReq, callback, redirectCount + 1, cancel);
                            
                            setDataCallback(new NullDataCallback());
                            return;
                        }
                        
                        // at this point the headers are done being modified
                        reportConnectedCompleted(cancel, null, this, callback);
                    }
                    
                    protected void onHeadersReceived() {
                        try {
                            if (cancel.isCancelled())
                                return;

                            if (scheduled != null)
                                mServer.removeAllCallbacks(scheduled);

                            // allow the middleware to massage the headers before the body is decoded

                            data.headers = mHeaders;
                            for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                                middleware.onHeadersReceived(data);
                            }
                            mHeaders = data.headers;
                            RawHeaders headers = mHeaders.getHeaders();
                            
                            // drop through, and setDataEmitter will be called for the body decoder.
                            // headers will be further massaged in there.
                        }
                        catch (Exception ex) {
                            reportConnectedCompleted(cancel, ex, null, callback);
                        }
                    };
                    
                    @Override
                    protected void report(Exception ex) {
                        if (cancel.isCancelled())
                            return;
                        if (ex instanceof AsyncSSLException) {
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
                                reportConnectedCompleted(cancel, ex, null, callback);
                        }
                        
                        data.exception = ex;
                        for (AsyncHttpClientMiddleware middleware: mMiddleware) {
                            middleware.onRequestComplete(data);
                        }
                    }


                    @Override
                    public AsyncSocket detachSocket() {
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
        data.connectCallback = socketConnected;

        for (AsyncHttpClientMiddleware middleware: mMiddleware) {
            if (null != (cancel.socketCancelable = middleware.getSocket(data)))
                return;
        }
        Assert.fail();
    }
    
    public Cancellable execute(URI uri, final HttpConnectCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }

    public Cancellable execute(String uri, final HttpConnectCallback callback) {
        return execute(new AsyncHttpGet(URI.create(uri)), callback);
    }
    
    public static abstract class RequestCallbackBase<T> implements RequestCallback<T> {
        @Override
        public void onProgress(AsyncHttpResponse response, int downloaded, int total) {
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
    
    private interface ResultConvert<T> {
        public T convert(ByteBufferList bb) throws Exception;
    }
    
    public Future<ByteBufferList> get(String uri, final DownloadCallback callback) {
        return get(uri, callback, new ResultConvert<ByteBufferList>() {
            @Override
            public ByteBufferList convert(ByteBufferList b) {
                return b;
            }
        });
    }
    
    public Future<String> get(String uri, final StringCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }
    
    public Future<String> execute(AsyncHttpRequest req, final StringCallback callback) {
        return execute(req, callback, new ResultConvert<String>() {
            @Override
            public String convert(ByteBufferList bb) {
                StringBuilder builder = new StringBuilder();
                for (ByteBuffer b: bb) {
                    builder.append(new String(b.array(), b.arrayOffset() + b.position(), b.remaining()));
                }
                return builder.toString();
            }
        });
    }

    public Future<JSONObject> get(String uri, final JSONObjectCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }

    public Future<JSONObject> execute(AsyncHttpRequest req, final JSONObjectCallback callback) {
        return execute(req, callback, new ResultConvert<JSONObject>() {
            @Override
            public JSONObject convert(ByteBufferList bb) throws JSONException {
                StringBuilder builder = new StringBuilder();
                for (ByteBuffer b: bb) {
                    builder.append(new String(b.array(), b.arrayOffset() + b.position(), b.remaining()));
                }
                return new JSONObject(builder.toString());
            }
        });
    }
    
    private void invoke(Handler handler, final RequestCallback callback, final AsyncHttpResponse response, final Exception e, final Object result) {
        if (callback == null)
            return;
        if (handler == null) {
            mServer.post(new Runnable() {
                @Override
                public void run() {
                    callback.onCompleted(e, response, result);
                }
            });
            return;
        }
        AsyncServer.post(handler, new Runnable() {
            @Override
            public void run() {
                callback.onCompleted(e, response, result);
            }
        });
    }
    
    private void invokeProgress(final RequestCallback callback, final AsyncHttpResponse response, final int downloaded, final int total) {
        if (callback != null)
            callback.onProgress(response, downloaded, total);
    }

    public Future<File> get(String uri, final String filename, final FileCallback callback) {
        return execute(new AsyncHttpGet(uri), filename, callback);
    }
    
    public Cancellable get(String uri, final DataSink sink, final CompletedCallback callback) {
        sink.setClosedCallback(callback);
        return execute(new AsyncHttpGet(URI.create(uri)), new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    callback.onCompleted(ex);
                    return;
                }
                com.koushikdutta.async.Util.pump(response, sink, callback);
            }
        });
    }

    public Future<File> execute(AsyncHttpRequest req, final String filename, final FileCallback callback) {
        final Handler handler = req.getHandler();
        final File file = new File(filename);
        CancelableImpl cancel = new CancelableImpl();
        final SimpleFuture<File> ret = new SimpleFuture<File>() {
            @Override
            public boolean cancel() {
                if (!super.cancel())
                    return false;
                file.delete();
                return true;
            }
        };
        ret.setParent(cancel);
        file.getParentFile().mkdirs();
        final FileOutputStream fout;
        try {
            fout = new FileOutputStream(file);
        }
        catch (FileNotFoundException e) {
            if (ret.setComplete(e))
                invoke(handler, callback, null, e, null);
            return ret;
        }
        execute(req, new HttpConnectCallback() {
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
                    if (ret.setComplete(ex))
                        invoke(handler, callback, response, ex, null);
                    return;
                }

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
                            if (ret.setComplete(ex))
                                invoke(handler, callback, response, ex, null);
                        }
                        else if (ret.setComplete(file)) {
                            invoke(handler, callback, response, null, file);
                        }
                    }
                });
            }
        }, 0, cancel);
        return ret;
    }
    
    private <T> SimpleFuture<T> execute(AsyncHttpRequest req, final RequestCallback callback, final ResultConvert<T> convert) {
        final SimpleFuture<T> ret = new SimpleFuture<T>();
        final Handler handler = req.getHandler();
        final CancelableImpl cancel = new CancelableImpl();
        execute(req, new HttpConnectCallback() {
            int mDownloaded = 0;
            ByteBufferList buffer = new ByteBufferList();
            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    if (ret.setComplete(ex))
                        invoke(handler, callback, response, ex, null);
                    return;
                }
                
                final int contentLength = response.getHeaders().getContentLength();

                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        mDownloaded += bb.remaining();
                        buffer.add(bb);
                        bb.clear();
                        invokeProgress(callback, response, mDownloaded, contentLength);
                    }
                });
                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex == null) {
                            try {
                                T value = convert.convert(buffer);
                                if (ret.setComplete(value))
                                    invoke(handler, callback, response, null, value);
                                return;
                            }
                            catch (Exception e) {
                                ex = e;
                            }
                        }
                        if (ret.setComplete(ex))
                            invoke(handler, callback, response, ex, null);
                    }
                });
            }
        }, 0, cancel);
        ret.setParent(cancel);
        return ret;
    }

    private <T> Future<T> get(String uri, final RequestCallback callback, final ResultConvert<T> convert) {
        return execute(new AsyncHttpGet(URI.create(uri)), callback, convert);
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
                    if (ret.setComplete(ex))
                        callback.onCompleted(ex, null);
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
                callback.onCompleted(ex, ws);
            }
        });
        
        ret.setParent(connect);
        return ret;
    }
    
    public Future<WebSocket> websocket(String uri, String protocol, final WebSocketConnectCallback callback) {
        final AsyncHttpGet get = new AsyncHttpGet(uri);
        return websocket(get, protocol, callback);
    }
    
    AsyncServer getServer() {
        return mServer;
    }
    
    public void setDebug(boolean debug) {
	mServer.mDebug = debug;
    }
}
