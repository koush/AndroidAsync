package com.koushikdutta.async.http.server;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.callback.ValueCallback;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.net.ssl.SSLContext;

@TargetApi(Build.VERSION_CODES.ECLAIR)
public class AsyncHttpServer extends AsyncHttpServerRouter {
    ArrayList<AsyncServerSocket> mListeners = new ArrayList<AsyncServerSocket>();
    public void stop() {
        if (mListeners != null) {
            for (AsyncServerSocket listener: mListeners) {
                listener.stop();
            }
        }
    }
    
    protected boolean onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        return false;
    }

    protected void onRequest(HttpServerRequestCallback callback, AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        if (callback != null) {
            try {
                callback.onRequest(request, response);
            }
            catch (Exception e) {
                Log.e("AsyncHttpServer", "request callback raised uncaught exception. Catching versus crashing process", e);
                response.code(500);
                response.end();
            }
        }
    }

    protected boolean isKeepAlive(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        return HttpUtil.isKeepAlive(response.getHttpVersion(), request.getHeaders());
    }

    protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
        return new UnknownRequestBody(headers.get("Content-Type"));
    }

    protected boolean isSwitchingProtocols(AsyncHttpServerResponse res) {
        return res.code() == 101;
    }

    ListenCallback mListenCallback = new ListenCallback() {
        @Override
        public void onAccepted(final AsyncSocket socket) {
            final AsyncHttpServerRequestImpl req = new AsyncHttpServerRequestImpl() {
                AsyncHttpServerRequestImpl self = this;
                HttpServerRequestCallback requestCallback;
                String fullPath;
                String path;
                boolean responseComplete;
                boolean requestComplete;
                AsyncHttpServerResponseImpl res;
                boolean hasContinued;

                final Runnable onFinally = new Runnable() {
                    @Override
                    public void run() {
                        Log.i("HTTP", "Done");
                    }
                };

                final ValueCallback<Exception> onException = new ValueCallback<Exception>() {
                    @Override
                    public void onResult(Exception value) {
                        Log.e("HTTP", "exception", value);
                    }
                };

                void onRequest() {
                    AsyncHttpServer.this.onRequest(requestCallback, this, res);
                }

                @Override
                protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
                    return AsyncHttpServer.this.onUnknownBody(headers);
                }

                @Override
                protected void onHeadersReceived() {
                    Headers headers = getHeaders();

                    // should the negotiation of 100 continue be here, or in the request impl?
                    // probably here, so AsyncResponse can negotiate a 100 continue.
                    if (!hasContinued && "100-continue".equals(headers.get("Expect"))) {
                        pause();
//                        System.out.println("continuing...");
                        Util.writeAll(mSocket, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(), new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                resume();
                                if (ex != null) {
                                    report(ex);
                                    return;
                                }
                                hasContinued = true;
                                onHeadersReceived();
                            }
                        });
                        return;
                    }
//                    System.out.println(headers.toHeaderString());
                    
                    String statusLine = getStatusLine();
                    String[] parts = statusLine.split(" ");
                    fullPath = parts[1];
                    path = URLDecoder.decode(fullPath.split("\\?")[0]);
                    method = parts[0];
                    RouteMatch route = route(method, path);
                    if (route != null) {
                        matcher = route.matcher;
                        requestCallback = route.callback;
                    }
                    res = new AsyncHttpServerResponseImpl(socket, this) {
                        @Override
                        protected void report(Exception e) {
                            super.report(e);
                            if (e != null) {
                                socket.setDataCallback(new NullDataCallback());
                                socket.setEndCallback(new NullCompletedCallback());
                                socket.close();
                            }
                        }

                        @Override
                        protected void onEnd() {
                            super.onEnd();
                            mSocket.setEndCallback(null);
                            responseComplete = true;
                            // reuse the socket for a subsequent request.
                            handleOnCompleted();
                        }
                    };
                    
                    boolean handled = AsyncHttpServer.this.onRequest(this, res);
                    if (handled)
                        return;

                    if (requestCallback == null) {
                        res.code(404);
                        res.end();
                        return;
                    }

                    if (!getBody().readFullyOnRequest() || requestComplete)
                        onRequest();
                }

                @Override
                public void onCompleted(Exception e) {
                    // if the protocol was switched off http, ignore this request/response.
                    if (isSwitchingProtocols(res))
                        return;
                    requestComplete = true;
                    super.onCompleted(e);
                    // no http pipelining, gc trashing if the socket dies
                    // while the request is being sent and is paused or something
                    mSocket.setDataCallback(new NullDataCallback() {
                        @Override
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                            super.onDataAvailable(emitter, bb);
                            mSocket.close();
                        }
                    });

                    if (e != null) {
                        mSocket.close();
                        return;
                    }

                    handleOnCompleted();

                    if (getBody().readFullyOnRequest()) {
                        onRequest();
                    }
                }
                
                private void handleOnCompleted() {
                    if (requestComplete && responseComplete) {
                        if (isKeepAlive(self, res)) {
                            onAccepted(socket);
                        }
                        else {
                            socket.close();
                        }
                    }
                }

                @Override
                public String getPath() {
                    return path;
                }

                @Override
                public Multimap getQuery() {
                    String[] parts = fullPath.split("\\?", 2);
                    if (parts.length < 2)
                        return new Multimap();
                    return Multimap.parseQuery(parts[1]);
                }

                @Override
                public String getUrl() {
                    return fullPath;
                }
            };
            req.setSocket(socket);
            socket.resume();
        }

        @Override
        public void onCompleted(Exception error) {
            report(error);
        }

        @Override
        public void onListening(AsyncServerSocket socket) {
            mListeners.add(socket);
        }
    };

    public AsyncServerSocket listen(AsyncServer server, int port) {
        return server.listen(null, port, mListenCallback);
    }

    private void report(Exception ex) {
        if (mCompletedCallback != null)
            mCompletedCallback.onCompleted(ex);
    }
    
    public AsyncServerSocket listen(int port) {
        return listen(AsyncServer.getDefault(), port);
    }

    public void listenSecure(final int port, final SSLContext sslContext) {
        AsyncServer.getDefault().listen(null, port, new ListenCallback() {
            @Override
            public void onAccepted(AsyncSocket socket) {
                AsyncSSLSocketWrapper.handshake(socket, null, port, sslContext.createSSLEngine(), null, null, false,
                new AsyncSSLSocketWrapper.HandshakeCallback() {
                    @Override
                    public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                        if (socket != null)
                            mListenCallback.onAccepted(socket);
                    }
                });
            }

            @Override
            public void onListening(AsyncServerSocket socket) {
                mListenCallback.onListening(socket);
            }

            @Override
            public void onCompleted(Exception ex) {
                mListenCallback.onCompleted(ex);
            }
        });
    }
    
    public ListenCallback getListenCallback() {
        return mListenCallback;
    }

    CompletedCallback mCompletedCallback;
    public void setErrorCallback(CompletedCallback callback) {
        mCompletedCallback = callback;        
    }

    public CompletedCallback getErrorCallback() {
        return mCompletedCallback;
    }

    private static Hashtable<Integer, String> mCodes = new Hashtable<Integer, String>();
    static {
        mCodes.put(200, "OK");
        mCodes.put(202, "Accepted");
        mCodes.put(206, "Partial Content");
        mCodes.put(101, "Switching Protocols");
        mCodes.put(301, "Moved Permanently");
        mCodes.put(302, "Found");
        mCodes.put(304, "Not Modified");
        mCodes.put(404, "Not Found");
    }
    
    public static String getResponseCodeDescription(int code) {
        String d = mCodes.get(code);
        if (d == null)
            return "Unknown";
        return d;
    }

    public static interface WebSocketRequestCallback {
        void onConnected(WebSocket webSocket, AsyncHttpServerRequest request);
    }

}
