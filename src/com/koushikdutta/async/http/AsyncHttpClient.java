package com.koushikdutta.async.http;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ResultCallback;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;

public class AsyncHttpClient {
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
        server.connectSocket(uri.getHost(), port, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (ex != null) {
                    callback.onConnectCompleted(ex, null);
                    return;
                }
                final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) {
                    protected void onHeadersReceived() {
                        super.onHeadersReceived();

                        try {
                            RawHeaders headers = getRawHeaders();
                            if ((headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || headers.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) && request.getFollowRedirect()) {
                                AsyncHttpRequest newReq = new AsyncHttpRequest(new URI(headers.get("Location")), request.getMethod());
                                connect(server, newReq, callback);
                                
                                setDataCallback(new NullDataCallback());
                            }
                        }
                        catch (Exception ex) {
                            callback.onConnectCompleted(ex, null);
                        }
                    };
                };
                ret.setSocket(socket);
                callback.onConnectCompleted(null, ret);
            }
        });
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
