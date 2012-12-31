package com.koushikdutta.async.http.server;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.UUID;

import android.util.Base64;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.libcore.RawHeaders;

public class WebSocketImpl implements WebSocket {
    private static String SHA1(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            byte[] sha1hash = md.digest();
            return Base64.encodeToString(sha1hash, 0);
        }
        catch (Exception ex) {
            return null;
        }
    }
    
    final static String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    private void setupParser() {
        mParser = new HybiParser(mSocket) {
            @Override
            protected void report(Exception ex) {
                if (WebSocketImpl.this.mExceptionCallback != null)
                    WebSocketImpl.this.mExceptionCallback.onCompleted(ex);
            }
            @Override
            protected void onMessage(byte[] payload) {
                if (WebSocketImpl.this.mDataCallback != null)
                    WebSocketImpl.this.mDataCallback.onDataAvailable(payload);
            }

            @Override
            protected void onMessage(String payload) {
                if (WebSocketImpl.this.mStringCallback != null)
                    WebSocketImpl.this.mStringCallback.onStringAvailable(payload);
            }
            @Override
            protected void onDisconnect(int code, String reason) {
                if (WebSocketImpl.this.mClosedCallback != null)
                    WebSocketImpl.this.mClosedCallback.onClosed();
            }
        };
        mParser.setMasking(false);
        if (mSocket.isPaused())
            mSocket.resume();
    }
    
    private AsyncSocket mSocket;
    BufferedDataSink mSink;
    public WebSocketImpl(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        this(request.getSocket());
        
        String key = request.getHeaders().getHeaders().get("Sec-WebSocket-Key");
        String concat = key + MAGIC;
        String sha1 = SHA1(concat);
        String origin = request.getHeaders().getHeaders().get("Origin");
        
        response.responseCode(101);
        response.getHeaders().getHeaders().set("Upgrade", "WebSocket");
        response.getHeaders().getHeaders().set("Connection", "Upgrade");
        response.getHeaders().getHeaders().set("Sec-WebSocket-Accept", sha1);
//        if (origin != null)
//            response.getHeaders().getHeaders().set("Access-Control-Allow-Origin", "http://" + origin);
        response.writeHead();
        
        setupParser();
    }
    
    public static void addWebSocketUpgradeHeaders(RawHeaders headers) {
        final String key = UUID.randomUUID().toString();
        headers.set("Sec-WebSocket-Key", key);
        headers.set("Connection", "Upgrade");
        headers.set("Upgrade", "websocket");
    }
    
    public WebSocketImpl(AsyncSocket socket) {
        mSocket = socket;
        mSink = new BufferedDataSink(mSocket);
        
        mSocket.setClosedCallback(new ClosedCallback() {
            @Override
            public void onClosed() {
                if (WebSocketImpl.this.mClosedCallback != null)
                    WebSocketImpl.this.mClosedCallback.onClosed();
            }
        });
    }
    
    public static WebSocket finishHandshake(RawHeaders requestHeaders, AsyncHttpResponse response) {
        if (response == null)
            return null;
        if (response.getHeaders().getHeaders().getResponseCode() != 101)
            return null;
        if (!"websocket".equalsIgnoreCase(response.getHeaders().getHeaders().get("Upgrade")))
            return null;
        
        // TODO: verify accept hash Sec-WebSocket-Accept
        
        WebSocketImpl ret = new WebSocketImpl(response.detachSocket());
        ret.setupParser();
        return ret;
    }
    
    HybiParser mParser;

    @Override
    public void close() {
    }

    ClosedCallback mClosedCallback;
    @Override
    public void setClosedCallback(ClosedCallback handler) {
        mClosedCallback = handler;
    }

    @Override
    public ClosedCallback getCloseHandler() {
        return mClosedCallback;
    }

    CompletedCallback mExceptionCallback;
    @Override
    public void setCompletedCallback(CompletedCallback callback) {
        mExceptionCallback = callback;
    }

    @Override
    public CompletedCallback getCompletedCallback() {
        return mExceptionCallback;
    }

    @Override
    public void send(byte[] bytes) {
        mSink.write(ByteBuffer.wrap(mParser.frame(bytes)));
    }

    @Override
    public void send(String string) {
        mSink.write(ByteBuffer.wrap(mParser.frame(string)));
    }

    StringCallback mStringCallback;
    @Override
    public void setStringCallback(StringCallback callback) {
        mStringCallback = callback;
    }

    DataCallback mDataCallback;
    @Override
    public void setDataCallback(DataCallback callback) {
        mDataCallback = callback;
    }

    @Override
    public StringCallback getStringCallback() {
        return mStringCallback;
    }

    @Override
    public DataCallback getDataCallback() {
        return mDataCallback;
    }

    @Override
    public boolean isOpen() {
        return mSocket.isOpen();
    }
}
