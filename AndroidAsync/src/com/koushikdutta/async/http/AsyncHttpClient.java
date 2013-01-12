package com.koushikdutta.async.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Hashtable;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Looper;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.RequestCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.stream.OutputStreamDataCallback;

public class AsyncHttpClient {
    private static abstract class InternalConnectCallback implements ConnectCallback {
        boolean reused = false;
    }

    private static Hashtable<String, HashSet<AsyncSocket>> mSockets = new Hashtable<String, HashSet<AsyncSocket>>();
    
    public static void execute(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        execute(AsyncServer.getDefault(), request, callback);
    }

    public static void execute(final AsyncServer server, final AsyncHttpRequest request, final HttpConnectCallback callback) {
        execute(server, request, callback, 0);
    }

    private static void execute(final AsyncServer server, final AsyncHttpRequest request, final HttpConnectCallback callback, final int redirectCount) {
        if (redirectCount > 5) {
            callback.onConnectCompleted(new Exception("too many redirects"), null);
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
                callback.onConnectCompleted(new Exception("invalid uri scheme"), null);
                return;
            }
        }
        final String lookup = uri.getScheme() + "//" + uri.getHost() + ":" + port;
        final int finalPort = port;

        final InternalConnectCallback socketConnected = new InternalConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (ex != null) {
                    callback.onConnectCompleted(ex, null);
                    return;
                }
                final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
                    boolean keepalive = false;
                    protected void onHeadersReceived() {
                        try {
                            RawHeaders headers = getRawHeaders();

                            String kas = headers.get("Connection");
                            if (kas != null && "keep-alive".toLowerCase().equals(kas.toLowerCase()))
                                keepalive = true;

                            if ((headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) && request.getFollowRedirect()) {
                                AsyncHttpRequest newReq = new AsyncHttpRequest(new URI(headers.get("Location")), request.getMethod());
                                execute(server, newReq, callback, redirectCount + 1);
                                
                                setDataCallback(new NullDataCallback());
                                return;
                            }

                            callback.onConnectCompleted(null, this);
                        }
                        catch (Exception ex) {
                            callback.onConnectCompleted(ex, null);
                        }
                    };
                    
                    @Override
                    protected void report(Exception ex) {
                        final AsyncSocket socket = getSocket();
                        if (socket == null)
                            return;
                        super.report(ex);
                        if (!socket.isOpen())
                            return;
                        if (ex != null)
                            return;
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
            synchronized (sockets) {
                for (final AsyncSocket socket: sockets) {
                    if (socket.isOpen()) {
                        sockets.remove(socket);
                        socket.setClosedCallback(null);
                        server.post(new Runnable() {
                            @Override
                            public void run() {
                                socketConnected.reused = true;
                                socketConnected.onConnectCompleted(null, socket);
                            }
                        });
                        return;
                    }
                }
            }
        }
        server.connectSocket(uri.getHost(), port, socketConnected);
    }
    
    public static void execute(URI uri, final HttpConnectCallback callback) {
        execute(AsyncServer.getDefault(), new AsyncHttpGet(uri), callback);
    }

    public static void execute(String uri, final HttpConnectCallback callback) {
        try {
            execute(AsyncServer.getDefault(), new AsyncHttpGet(new URI(uri)), callback);
        }
        catch (URISyntaxException e) {
            callback.onConnectCompleted(e, null);
        }
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
    
    public static void get(String uri, final DownloadCallback callback) {
        get(uri, callback, new ResultConvert() {
            @Override
            public Object convert(ByteBufferList b) {
                return b;
            }
        });
    }
    
    public static void get(String uri, final StringCallback callback) {
        try {
            execute(new AsyncHttpGet(uri), callback);
        }
        catch (URISyntaxException e) {
            callback.onCompleted(e, null, null);
        }
    }
    
    public static void execute(AsyncHttpRequest req, final StringCallback callback) {
        execute(req, callback, new ResultConvert() {
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

    public static void get(String uri, final JSONObjectCallback callback) {
        try {
            execute(new AsyncHttpGet(uri), callback);
        }
        catch (URISyntaxException e) {
            callback.onCompleted(e, null, null);
        }
    }

    public static void execute(AsyncHttpRequest req, final JSONObjectCallback callback) {
        execute(req, callback, new ResultConvert() {
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
    
    private static void invoke(Handler handler, final RequestCallback callback, final AsyncHttpResponse response, final Exception e, final Object result) {
        if (callback == null)
            return;
        if (handler == null) {
            callback.onCompleted(e, response, result);
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onCompleted(e, response, result);
            }
        });
    }
    
    private static void invokeProgress(Handler handler, final RequestCallback callback, final AsyncHttpResponse response, final int downloaded, final int total) {
        if (callback == null)
            return;
        if (handler == null) {
            callback.onProgress(response, downloaded, total);
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onProgress(response, downloaded, total);
            }
        });
    }

    public static void get(String uri, final String filename, final FileCallback callback) {
        try {
            execute(new AsyncHttpGet(uri), filename, callback);
        }
        catch (URISyntaxException e) {
            callback.onCompleted(e, null, null);
        }
    }
    
    public static interface WebSocketConnectCallback {
        public void onCompleted(Exception ex, WebSocket webSocket);
    }
    
    public static void websocket(final AsyncHttpRequest req, String protocol, final WebSocketConnectCallback callback) {
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
    
    public static void websocket(String uri, String protocol, final WebSocketConnectCallback callback) {
        try {
            final AsyncHttpGet get = new AsyncHttpGet(uri);
            websocket(get, protocol, callback);
        }
        catch (URISyntaxException e) {
            callback.onCompleted(e, null);
        }
    }
    
    public static void execute(AsyncHttpRequest req, final String filename, final FileCallback callback) {
        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        final File file = new File(filename);
        file.getParentFile().mkdirs();
        final FileOutputStream fout;
        try {
            fout = new FileOutputStream(file);
        }
        catch (FileNotFoundException e) {
            invoke(handler, callback, null, e, null);
            return;
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
                    invoke(handler, callback, response, ex, null);
                    return;
                }
                
                final int contentLength = response.getHeaders().getContentLength();
                
                response.setDataCallback(new OutputStreamDataCallback(fout) {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        mDownloaded += bb.remaining();
                        super.onDataAvailable(emitter, bb);
                        invokeProgress(handler, callback, response, mDownloaded, contentLength);
                    }
                });
                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        try {
                            fout.close();
                        }
                        catch (IOException e) {
                            invoke(handler, callback, response, e, null);
                            return;
                        }
                        if (ex != null) {
                            invoke(handler, callback, response, ex, null);
                            return;
                        }
                        invoke(handler, callback, response, null, file);
                    }
                });
            }
        });
    }
    
    private static void execute(AsyncHttpRequest req, final RequestCallback callback, final ResultConvert convert) {
        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        execute(req, new HttpConnectCallback() {
            int mDownloaded = 0;
            ByteBufferList buffer = new ByteBufferList();
            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
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
                        invokeProgress(handler, callback, response, mDownloaded, contentLength);
                    }
                });
                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        try {
                            Object value = convert.convert(buffer);
                            invoke(handler, callback, response, ex, buffer != null ? value : null);
                        }
                        catch (Exception e) {
                            invoke(handler, callback, response, e, null);
                        }
                    }
                });
            }
        });
    }

    private static void get(String uri, final RequestCallback callback, final ResultConvert convert) {
        try {
            execute(new AsyncHttpGet(new URI(uri)), callback, convert);
        }
        catch (URISyntaxException e) {
            callback.onCompleted(e, null, null);
        }
    }
}
