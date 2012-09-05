package com.koushikdutta.async.http.server;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import android.util.Base64;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ExceptionCallback;
import com.koushikdutta.async.callback.ClosedCallback;

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
    
    private AsyncSocket mSocket;
    BufferedDataSink mSink;
    public WebSocketImpl(AsyncHttpServerRequestImpl request, AsyncHttpServerResponse response) {
        mSocket = request.mSocket;
        mSink = new BufferedDataSink(mSocket);
        
        String key = request.getHeaders().getHeaders().get("Sec-WebSocket-Key");
        String concat = key + MAGIC;
        String sha1 = SHA1(concat);
        String origin = request.getHeaders().getHeaders().get("Origin");
        
        response.responseCode(101);
        response.getHeaders().getHeaders().set("Upgrade", "WebSocket");
        response.getHeaders().getHeaders().set("Connection", "Upgrade");
        response.getHeaders().getHeaders().set("Sec-WebSocket-Accept", sha1);
        if (origin != null)
            response.getHeaders().getHeaders().set("Access-Control-Allow-Origin", "http://" + origin);
        response.writeHead();
        
        mParser = new HybiParser(request) {
            @Override
            protected void report(Exception ex) {
                if (WebSocketImpl.this.mExceptionCallback != null)
                    WebSocketImpl.this.mExceptionCallback.onException(ex);
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

    ExceptionCallback mExceptionCallback;
    @Override
    public void setExceptionCallback(ExceptionCallback callback) {
        mExceptionCallback = callback;        
    }

    @Override
    public ExceptionCallback getExceptionCallback() {
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
}
