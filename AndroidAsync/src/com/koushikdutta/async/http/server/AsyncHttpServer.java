package com.koushikdutta.async.http.server;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Base64;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.ExceptionCallback;
import com.koushikdutta.async.ExceptionEmitter;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;

public class AsyncHttpServer implements ExceptionEmitter {
    public AsyncHttpServer(AsyncServer server, int port) {
        server.listen(null, port, new ListenCallback() {
            @Override
            public void onAccepted(final AsyncSocket socket) {
                AsyncHttpServerRequestImpl req = new AsyncHttpServerRequestImpl() {
                    @Override
                    protected void onHeadersReceived() {
                        super.onHeadersReceived();
                        RawHeaders headers = getRawHeaders();
                        
                        String statusLine = headers.getStatusLine();
                        String[] parts = statusLine.split(" ");
                        String path = parts[1];
                        String action = parts[0];
                        Pair match = null;
                        synchronized (mActions) {
                            ArrayList<Pair> pairs = mActions.get(action);
                            if (pairs != null) {
                                for (Pair p: pairs) {
                                    Matcher m = p.regex.matcher(path);
                                    if (m.matches()) {
                                        match = p;
                                        break;
                                    }
                                }
                            }
                        }
                        AsyncHttpServerResponseImpl res = new AsyncHttpServerResponseImpl(socket);
                        if (match == null) {
                            res.responseCode(404);
                            res.end();
                            return;
                        }
                        
                        match.callback.onRequest(this, res);
                    }
                    @Override
                    protected void report(Exception e) {
                        super.report(e);
                        AsyncHttpServer.this.report(e);
                    }
                };
                req.setSocket(socket);
            }

            @Override
            public void onException(Exception error) {
                report(error);
            }
        });
    }
    
    private void report(Exception ex) {
        if (mExceptionCallback != null)
            mExceptionCallback.onException(ex);
    }
    
    public AsyncHttpServer(int port) {
        this(AsyncServer.getDefault(), port);
    }

    ExceptionCallback mExceptionCallback;
    @Override
    public void setExceptionCallback(ExceptionCallback callback) {
        mExceptionCallback = callback;        
    }

    @Override
    public ExceptionCallback getExceptionCallback() {
        return mExceptionCallback;
    }
    
    private static class Pair {
        Pattern regex;
        HttpServerRequestCallback callback;
    }
    
    Hashtable<String, ArrayList<Pair>> mActions = new Hashtable<String, ArrayList<Pair>>();
    
    private void addAction(String action, String regex, HttpServerRequestCallback callback) {
        Pair p = new Pair();
        p.regex = Pattern.compile(regex);
        p.callback = callback;
        
        synchronized (mActions) {
            ArrayList<Pair> pairs = mActions.get(action);
            if (pairs == null) {
                pairs = new ArrayList<AsyncHttpServer.Pair>();
                mActions.put(action, pairs);
            }
            pairs.add(p);
        }
    }

    public void websocket(String regex, final WebSocketCallback callback) {
        get(regex, new HttpServerRequestCallback() {
            @Override
            public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                boolean hasUpgrade = false;
                String connection = request.getHeaders().getHeaders().get("Connection");
                if (connection != null) {
                    String[] connections = connection.split(",");
                    for (String c: connections) {
                        if ("Upgrade".equalsIgnoreCase(c.trim())) {
                            hasUpgrade = true;
                            break;
                        }
                    }
                }
                if (!"websocket".equals(request.getHeaders().getHeaders().get("Upgrade")) || !hasUpgrade) {
                    response.responseCode(404);
                    response.end();
                    return;
                }
                AsyncHttpServerRequestImpl impl = (AsyncHttpServerRequestImpl)request;
                
                callback.onConnected(new WebSocketImpl(impl, response));
            }
        });
    }
    
    public void get(String regex, HttpServerRequestCallback callback) {
        addAction("GET", regex, callback);
    }
    
    
    private static Hashtable<Integer, String> mCodes = new Hashtable<Integer, String>();
    static {
        mCodes.put(200, "OK");
        mCodes.put(101, "Switching Protocols");
        mCodes.put(404, "Not Found");
    }
    
    public static String getResponseCodeDescription(int code) {
        String d = mCodes.get(code);
        if (d == null)
            return "Unknown";
        return d;
    }
}
