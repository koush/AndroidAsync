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

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataExchange;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.SSLDataExchange;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ResultPairCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.stream.OutputStreamDataCallback;

public class AsyncHttpClient {
    private static class InternalConnectCallback implements ConnectCallback {
        DataExchange exchange;
        @Override
        public void onConnectCompleted(Exception ex, AsyncSocket socket) {
            // TODO Auto-generated method stub
            
        }
        
    }
    
    private static class SocketExchange {
        AsyncSocket socket;
        DataExchange exchange;
    }
    private static Hashtable<String, HashSet<SocketExchange>> mSockets = new Hashtable<String, HashSet<SocketExchange>>();
    
    public static void connect(final AsyncHttpRequest request, final HttpConnectCallback callback) {
        connect(AsyncServer.getDefault(), request, callback);
    }

    public static void connect(final AsyncServer server, final AsyncHttpRequest request, final HttpConnectCallback callback) {
        connect(server, request, callback, 0);
    }

    private static void connect(final AsyncServer server, final AsyncHttpRequest request, final HttpConnectCallback callback, int redirectCount) {
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
                            HashSet<SocketExchange> sockets = mSockets.get(lookup);
                            if (sockets == null) {
                                sockets = new HashSet<SocketExchange>();
                                mSockets.put(lookup, sockets);
                            }
                            final HashSet<SocketExchange> ss = sockets;
                            synchronized (sockets) {
                                SocketExchange se = new SocketExchange();
                                se.socket = socket;
                                se.exchange = exchange;
                                sockets.add(se);
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
                // socket and exchange are the same for regular http
                // but different for https (ssl)
                // the exchange will be a wrapper around socket that does
                // ssl translation.
                if (exchange == null) {
                    exchange = socket;
                    if (request.getUri().getScheme().equals("https")) {
                        SSLDataExchange ssl = new SSLDataExchange(socket, uri.getHost(), finalPort);
                        exchange = ssl;
                        socket.setDataCallback(ssl);
                    }
                }

                ret.setSocket(socket, exchange);
                callback.onConnectCompleted(null, ret);
            }
        };

        HashSet<SocketExchange> sockets = mSockets.get(lookup);
        if (sockets != null) {
            synchronized (sockets) {
                for (final SocketExchange se: sockets) {
                    final AsyncSocket socket = se.socket;
                    if (socket.isConnected()) {
                        sockets.remove(se);
                        socket.setClosedCallback(null);
                        server.post(new Runnable() {
                            @Override
                            public void run() {
                                socketConnected.exchange = se.exchange;
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
    
    public static interface DownloadCallback extends ResultPairCallback<AsyncHttpResponse, ByteBufferList> {
    }
    
    public static interface StringCallback extends ResultPairCallback<AsyncHttpResponse, String> {
    }

    public static interface JSONObjectCallback extends ResultPairCallback<AsyncHttpResponse, JSONObject> {
    }
    
    public static interface FileCallback extends ResultPairCallback<AsyncHttpResponse, File> {
    }
    
    private interface ResultConvert {
        public Object convert(ByteBufferList bb) throws Exception;
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

    public static void download(String uri, final JSONObjectCallback callback) {
        download(uri, callback, new ResultConvert() {
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

    public static void download(AsyncHttpRequest req, final JSONObjectCallback callback) {
        download(req, callback, new ResultConvert() {
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
    
    private static void invoke(Handler handler, final ResultPairCallback callback, final AsyncHttpResponse response, final Exception e, final Object result) {
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
    
    public static void download(String uri, final String filename, final FileCallback callback) {
        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        final File file = new File(filename);
        final FileOutputStream fout;
        try {
            fout = new FileOutputStream(file);
        }
        catch (FileNotFoundException e) {
            invoke(handler, callback, null, e, null);
            return;
        }
        connect(uri, new HttpConnectCallback() {
            ByteBufferList buffer = new ByteBufferList();
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
                
                response.setDataCallback(new OutputStreamDataCallback(fout));
                response.setCompletedCallback(new CompletedCallback() {
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
    
    private static void download(AsyncHttpRequest req, final ResultPairCallback callback, final ResultConvert convert) {
        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        connect(req, new HttpConnectCallback() {
            ByteBufferList buffer = new ByteBufferList();
            @Override
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    invoke(handler, callback, response, ex, null);
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

    private static void download(String uri, final ResultPairCallback callback, final ResultConvert convert) {
        try {
            download(new AsyncHttpGet(new URI(uri)), callback, convert);
        }
        catch (URISyntaxException e) {
            callback.onCompleted(e, null, null);
        }
    }
}
