package com.koushikdutta.async.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.TimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.koushikdutta.async.AsyncSSLException;
import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.Cancelable;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.SimpleCancelable;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.RequestCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.stream.OutputStreamDataCallback;

public class AsyncHttpClient {
    private static AsyncHttpClient mDefault;
    public static AsyncHttpClient getDefault() {
        if (mDefault == null)
            mDefault = new AsyncHttpClient(AsyncServer.getDefault());
        return mDefault;
    }
    
    private Hashtable<String, HashSet<AsyncSocket>> mSockets = new Hashtable<String, HashSet<AsyncSocket>>();
    AsyncServer mServer;
    public AsyncHttpClient(AsyncServer server) {
        mServer = server;
    }

    private static abstract class InternalConnectCallback implements ConnectCallback {
        boolean reused = false;
    }

    public Cancelable execute(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        CancelableImpl ret = new CancelableImpl();
        execute(request, callback, 0, ret = new CancelableImpl());
        return ret;
    }
    
    private static class CancelableImpl extends SimpleCancelable {
    }
    
    private void reportConnectedCompleted(CancelableImpl cancel, Exception ex, AsyncHttpResponseImpl response, final HttpConnectCallback callback) {
        cancel.setComplete(true);
        callback.onConnectCompleted(ex, response);
    }

    private void execute(final AsyncHttpRequest request, final HttpConnectCallback callback, final int redirectCount, final CancelableImpl cancel) {
        if (redirectCount > 5) {
            reportConnectedCompleted(cancel, new Exception("too many redirects"), null, callback);
            return;
        }
        final URI uri = request.getUri();
        int port = uri.getPort();
        if (port == -1) {
            if (uri.getScheme().equals("http"))
                port = 80;
            else if (uri.getScheme().equals("https"))
                port = 443;
            else {
                reportConnectedCompleted(cancel, new Exception("invalid uri scheme"), null, callback);
                return;
            }
        }
        final String lookup = uri.getScheme() + "//" + uri.getHost() + ":" + port;
        final int finalPort = port;

        final InternalConnectCallback socketConnected = new InternalConnectCallback() {
            AsyncSocket cancelSocket;
            Object scheduled;
            {
                if (request.getTimeout() > 0) {
                    scheduled = mServer.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            cancel.cancel();
                            if (cancelSocket != null)
                                cancelSocket.close();
                            reportConnectedCompleted(cancel, new TimeoutException(), null, callback);
                        }
                    }, request.getTimeout());
                }
                else {
                    scheduled = null;
                }
            }
            
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (cancel.isCanceled()) {
                    if (socket != null)
                        socket.close();
                    return;
                }
                cancelSocket = socket;
                if (ex != null) {
                    reportConnectedCompleted(cancel, ex, null, callback);
                    return;
                }
                final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
                    boolean keepalive = false;
                    boolean headersReceived;
                    protected void onHeadersReceived() {
                        try {
                            if (cancel.isCanceled())
                                return;

                            if (scheduled != null)
                                mServer.removeAllCallbacks(scheduled);
                            headersReceived = true;
                            RawHeaders headers = getRawHeaders();

                            String kas = headers.get("Connection");
                            if (kas != null && "keep-alive".toLowerCase().equals(kas.toLowerCase()))
                                keepalive = true;

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

                            reportConnectedCompleted(cancel, null, this, callback);
                        }
                        catch (Exception ex) {
                            reportConnectedCompleted(cancel, ex, null, callback);
                        }
                    };
                    
                    @Override
                    protected void report(Exception ex) {
                        if (cancel.isCanceled())
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
                            if (!headersReceived && ex != null)
                                reportConnectedCompleted(cancel, ex, null, callback);
                            return;
                        }
                        if (!keepalive) {
                            socket.close();
                        }
                        else {
                            HashSet<AsyncSocket> sockets = mSockets.get(lookup);
                            if (sockets == null) {
                                sockets = new HashSet<AsyncSocket>();
                                mSockets.put(lookup, sockets);
                            }
                            final HashSet<AsyncSocket> ss = sockets;
                            synchronized (sockets) {
                                sockets.add(socket);
                                socket.setClosedCallback(new CompletedCallback() {
                                    @Override
                                    public void onCompleted(Exception ex) {
                                        synchronized (ss) {
                                            ss.remove(socket);
                                        }
                                        socket.setClosedCallback(null);
                                    }
                                });
                            }
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

                    @Override
                    public boolean isReusedSocket() {
                        return reused;
                    }
                };

                // if this socket is not being reused,
                // check to see if an AsyncSSLSocket needs to be wrapped around it.
                if (!reused) {
                    if (request.getUri().getScheme().equals("https")) {
                        socket = new AsyncSSLSocket(socket, uri.getHost(), finalPort);
                    }
                }

                ret.setSocket(socket);
            }
        };

        HashSet<AsyncSocket> sockets = mSockets.get(lookup);
        if (sockets != null) {
//            synchronized (sockets) {
                for (final AsyncSocket socket: sockets) {
                    if (socket.isOpen()) {
                        sockets.remove(socket);
                        socket.setClosedCallback(null);
                        mServer.post(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("Async", "Reusing socket.");
                                socketConnected.reused = true;
                                socketConnected.onConnectCompleted(null, socket);
                            }
                        });
                        return;
                    }
                }
//            }
        }
        mServer.connectSocket(uri.getHost(), port, socketConnected);
    }
    
    public Cancelable execute(URI uri, final HttpConnectCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }

    public Cancelable execute(String uri, final HttpConnectCallback callback) {
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
    
    private interface ResultConvert {
        public Object convert(ByteBufferList bb) throws Exception;
    }
    
    public Cancelable get(String uri, final DownloadCallback callback) {
        return get(uri, callback, new ResultConvert() {
            @Override
            public Object convert(ByteBufferList b) {
                return b;
            }
        });
    }
    
    public Cancelable get(String uri, final StringCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }
    
    public Cancelable execute(AsyncHttpRequest req, final StringCallback callback) {
        return execute(req, callback, new ResultConvert() {
            @Override
            public Object convert(ByteBufferList bb) {
                StringBuilder builder = new StringBuilder();
                for (ByteBuffer b: bb) {
                    builder.append(new String(b.array(), b.arrayOffset() + b.position(), b.remaining()));
                }
                return builder.toString();
            }
        });
    }

    public Cancelable get(String uri, final JSONObjectCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }

    public Cancelable execute(AsyncHttpRequest req, final JSONObjectCallback callback) {
        return execute(req, callback, new ResultConvert() {
            @Override
            public Object convert(ByteBufferList bb) throws JSONException {
                StringBuilder builder = new StringBuilder();
                for (ByteBuffer b: bb) {
                    builder.append(new String(b.array(), b.arrayOffset() + b.position(), b.remaining()));
                }
                return new JSONObject(builder.toString());
            }
        });
    }
    
    private void invoke(Handler handler, final RequestCallback callback, final AsyncServer server, final AsyncHttpResponse response, final Exception e, final Object result) {
        if (callback == null)
            return;
        if (handler == null) {
            server.post(new Runnable() {
                @Override
                public void run() {
                    callback.onCompleted(e, response, result);
                }
            });
            return;
        }
        handler.post(new Runnable() {
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

    public Cancelable get(String uri, final String filename, final FileCallback callback) {
        return execute(new AsyncHttpGet(uri), filename, callback);
    }
    
    public Cancelable get(String uri, final DataSink sink, final CompletedCallback callback) {
        sink.setClosedCallback(callback);
        return execute(new AsyncHttpGet(URI.create(uri)), new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    callback.onCompleted(ex);
                    return;
                }
                com.koushikdutta.async.Util.pump(response,  sink, callback);
            }
        });
    }

    public Cancelable execute(AsyncHttpRequest req, final String filename, final FileCallback callback) {
        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        final File file = new File(filename);
        final CancelableRequest cancel = new CancelableRequest() {
            @Override
            public Cancelable cancel() {
                Cancelable ret = super.cancel();
                file.delete();
                return ret;
            }
        };
        file.getParentFile().mkdirs();
        final FileOutputStream fout;
        try {
            fout = new FileOutputStream(file);
        }
        catch (FileNotFoundException e) {
            invoke(handler, callback, AsyncServer.getDefault(), null, e, null);
            return SimpleCancelable.COMPLETED;
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
                    invoke(handler, callback, AsyncServer.getDefault(), response, ex, null);
                    return;
                }
                cancel.response = response;

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
                            invoke(handler, callback, AsyncServer.getDefault(), response, e, null);
                            return;
                        }
                        if (ex != null) {
                            file.delete();
                            invoke(handler, callback, AsyncServer.getDefault(), response, ex, null);
                            return;
                        }
                        invoke(handler, callback, AsyncServer.getDefault(), response, null, file);
                    }
                });
            }
        }, 0, cancel);
        return cancel;
    }
    
    private static class CancelableRequest extends CancelableImpl {
        AsyncHttpResponse response;
        @Override
        public Cancelable cancel() {
            Cancelable ret = super.cancel();
            if (response != null)
                response.close();
            return ret;
        }
    }
    
    private Cancelable execute(AsyncHttpRequest req, final RequestCallback callback, final ResultConvert convert) {
        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        final CancelableRequest cancel = new CancelableRequest();
        execute(req, new HttpConnectCallback() {
            int mDownloaded = 0;
            ByteBufferList buffer = new ByteBufferList();
            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    invoke(handler, callback, AsyncServer.getDefault(), response, ex, null);
                    return;
                }
                cancel.response = response;
                
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
                        try {
                            Object value = convert.convert(buffer);
                            invoke(handler, callback, AsyncServer.getDefault(), response, ex, buffer != null ? value : null);
                        }
                        catch (Exception e) {
                            invoke(handler, callback, AsyncServer.getDefault(), response, e, null);
                        }
                    }
                });
            }
        }, 0, cancel);
        return cancel;
    }

    private Cancelable get(String uri, final RequestCallback callback, final ResultConvert convert) {
        return execute(new AsyncHttpGet(URI.create(uri)), callback, convert);
    }

    
    public static interface WebSocketConnectCallback {
        public void onCompleted(Exception ex, WebSocket webSocket);
    }
    
    public void websocket(final AsyncHttpRequest req, String protocol, final WebSocketConnectCallback callback) {
        WebSocketImpl.addWebSocketUpgradeHeaders(req.getHeaders().getHeaders(), protocol);
        execute(req, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    callback.onCompleted(ex, null);
                    return;
                }
                WebSocket ws = WebSocketImpl.finishHandshake(req.getHeaders().getHeaders(), response);
                if (ws == null)
                    ex = new Exception("Unable to complete websocket handshake");
                callback.onCompleted(ex, ws);
            }
        });
    }
    
    public void websocket(String uri, String protocol, final WebSocketConnectCallback callback) {
        final AsyncHttpGet get = new AsyncHttpGet(uri);
        websocket(get, protocol, callback);
    }
}
