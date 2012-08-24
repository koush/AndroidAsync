package com.koushikdutta.async.http;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ResultCallback;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;

public class AsyncHttpClient {
    private static Hashtable<String, HashSet<AsyncSocket>> mSockets = new Hashtable<String, HashSet<AsyncSocket>>();
    
    public static void connect(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        connect(AsyncServer.getDefault(), request, callback);
    }

    public static void connect(final AsyncServer server, final AsyncHttpRequest request, final HttpConnectCallback callback) {
        connect(server, request, callback, 0);
    }

    public static void connect(final AsyncServer server, final AsyncHttpRequest request, final HttpConnectCallback callback, int redirectCount) {
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

        ConnectCallback socketConnected = new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, final AsyncSocket socket) {
                if (ex != null) {
                    callback.onConnectCompleted(ex, null);
                    return;
                }
                final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
                    boolean keepalive = false;
                    protected void onHeadersReceived() {
                        super.onHeadersReceived();

                        try {
                            RawHeaders headers = getRawHeaders();
                            if ((headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) && request.getFollowRedirect()) {
                                AsyncHttpRequest newReq = new AsyncHttpRequest(new URI(headers.get("Location")), request.getMethod());
                                connect(server, newReq, callback);
                                
                                setDataCallback(new NullDataCallback());
                            }
                            
                            String kas = headers.get("Connection");
                            if (kas != null && "keep-alive".toLowerCase().equals(kas.toLowerCase()))
                                keepalive = true;
                        }
                        catch (Exception ex) {
                            callback.onConnectCompleted(ex, null);
                        }
                    };
                    
                    protected void onCompleted(Exception ex) {
                        super.onCompleted(ex);
                        if (!keepalive) {
                            socket.close();
                        }
                        else {
                            HashSet<AsyncSocket> sockets = mSockets.get(uri.getHost());
                            if (sockets == null) {
                                sockets = new HashSet<AsyncSocket>();
                                mSockets.put(uri.getHost(), sockets);
                            }
                            final HashSet<AsyncSocket> ss = sockets;
                            synchronized (sockets) {
                                sockets.add(socket);
                                socket.setClosedCallback(new ClosedCallback() {
                                    @Override
                                    public void onClosed() {
                                        synchronized (ss) {
                                            ss.remove(socket);
                                        }
                                        socket.setClosedCallback(null);
                                    }
                                });
                            }
                        }
                    };
                };
                ret.setSocket(socket);
                callback.onConnectCompleted(null, ret);
            }
        };

        HashSet<AsyncSocket> sockets = mSockets.get(uri.getHost());
        if (sockets != null) {
            synchronized (sockets) {
                for (AsyncSocket socket: sockets) {
                    if (socket.isConnected()) {
                        socket.setClosedCallback(null);
                        socketConnected.onConnectCompleted(null, socket);
                        return;
                    }
                }
            }
        }
        server.connectSocket(uri.getHost(), port, socketConnected);
    }
    
    public static void connect(URI uri, final HttpConnectCallback callback) {
        connect(AsyncServer.getDefault(), new AsyncHttpGet(uri), callback);
    }

    public static void connect(String uri, final HttpConnectCallback callback) {
        try {
            connect(AsyncServer.getDefault(), new AsyncHttpGet(new URI(uri)), callback);
        }
        catch (URISyntaxException e) {
            callback.onConnectCompleted(e, null);
        }
    }
    
    public static interface DownloadCallback extends ResultCallback<ByteBufferList> {
    }
    
    public static interface StringCallback extends ResultCallback<String> {
    }
    
    private interface ResultConvert {
        public Object convert(ByteBufferList bb);
    }
    
    public static void download(String uri, final DownloadCallback callback) {
        download(uri, callback, new ResultConvert() {
            @Override
            public Object convert(ByteBufferList b) {
                return b;
            }
        });
    }
    
    public static void download(String uri, final StringCallback callback) {
        download(uri, callback, new ResultConvert() {
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
    
    private static void download(String uri, final ResultCallback callback, final ResultConvert convert) {
        connect(uri, new HttpConnectCallback() {
            ByteBufferList buffer = new ByteBufferList();
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    callback.onCompleted(ex, null);
                    return;
                }
                
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        buffer.add(bb);
                        bb.clear();
                    }
                });
                response.setCompletedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        callback.onCompleted(ex, buffer != null ? convert.convert(buffer) : null);
                    }
                });
            }
        });
    }
}
