package com.koushikdutta.async.http;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.UUID;

import android.text.TextUtils;
import android.util.Base64;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;

public class WebSocketImpl implements WebSocket {
    @Override
    public void end() {
        mSocket.end();
    }
    
    private static byte[] toByteArray(UUID uuid) {
    	byte[] byteArray = new byte[(Long.SIZE / Byte.SIZE) * 2];
    	ByteBuffer buffer = ByteBuffer.wrap(byteArray);
    	LongBuffer longBuffer = buffer.asLongBuffer();
    	longBuffer.put(new long[] { uuid.getMostSignificantBits(),uuid.getLeastSignificantBits() });
    	return byteArray;
    }

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
    
    private LinkedList<ByteBufferList> pending;

    private void addAndEmit(ByteBufferList bb) {
        if (pending == null) {
            Util.emitAllData(this, bb);
            if (bb.remaining() > 0) {
                pending = new LinkedList<ByteBufferList>();
                pending.add(bb);
            }
            return;
        }
        
        while (!isPaused()) {
            bb = pending.remove();
            Util.emitAllData(this, bb);
            if (bb.remaining() > 0)
                pending.add(0, bb);
        }
        if (pending.size() == 0)
            pending = null;
    }

    private void setupParser(boolean masking, boolean deflate) {
        mParser = new HybiParser(mSocket) {
            @Override
            protected void report(Exception ex) {
                if (WebSocketImpl.this.mExceptionCallback != null)
                    WebSocketImpl.this.mExceptionCallback.onCompleted(ex);
            }
            @Override
            protected void onMessage(byte[] payload) {
                addAndEmit(new ByteBufferList(payload));
            }

            @Override
            protected void onMessage(String payload) {
                if (WebSocketImpl.this.mStringCallback != null)
                    WebSocketImpl.this.mStringCallback.onStringAvailable(payload);
            }
            @Override
            protected void onDisconnect(int code, String reason) {
                mSocket.close();
//                if (WebSocketImpl.this.mClosedCallback != null)
//                    WebSocketImpl.this.mClosedCallback.onCompleted(null);
            }
            @Override
            protected void sendFrame(byte[] frame) {
                mSink.write(ByteBuffer.wrap(frame));
            }
        };
        mParser.setMasking(masking);
        mParser.setDeflate(deflate);
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
        String protocol = request.getHeaders().getHeaders().get("Sec-WebSocket-Protocol");
        // match the protocol (sanity checking and enforcement is done in the caller)
        if (!TextUtils.isEmpty(protocol))
            response.getHeaders().getHeaders().set("Sec-WebSocket-Protocol", protocol);
//        if (origin != null)
//            response.getHeaders().getHeaders().set("Access-Control-Allow-Origin", "http://" + origin);
        response.writeHead();
        
        setupParser(false, false);
    }
    
    public static void addWebSocketUpgradeHeaders(AsyncHttpRequest req, String protocol) {
        RawHeaders headers = req.getHeaders().getHeaders();
        final String key = Base64.encodeToString(toByteArray(UUID.randomUUID()),Base64.NO_WRAP);
        headers.set("Sec-WebSocket-Version", "13");
        headers.set("Sec-WebSocket-Key", key);
        headers.set("Sec-WebSocket-Extensions", "x-webkit-deflate-frame");
        headers.set("Connection", "Upgrade");
        headers.set("Upgrade", "websocket");
        if (protocol != null)
            headers.set("Sec-WebSocket-Protocol", protocol);
        headers.set("Pragma", "no-cache");
        headers.set("Cache-Control", "no-cache");
        req.getHeaders().setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/27.0.1453.15 Safari/537.36");
    }
    
    public WebSocketImpl(AsyncSocket socket) {
        mSocket = socket;
        mSink = new BufferedDataSink(mSocket);
    }
    
    public static WebSocket finishHandshake(RawHeaders requestHeaders, AsyncHttpResponse response) {
        if (response == null)
            return null;
        if (response.getHeaders().getHeaders().getResponseCode() != 101)
            return null;
        if (!"websocket".equalsIgnoreCase(response.getHeaders().getHeaders().get("Upgrade")))
            return null;
        
        String sha1 = response.getHeaders().getHeaders().get("Sec-WebSocket-Accept");
        if (sha1 == null)
            return null;
        String key = requestHeaders.get("Sec-WebSocket-Key");
        if (key == null)
            return null;
        String concat = key + MAGIC;
        String expected = SHA1(concat).trim();
        if (!sha1.equalsIgnoreCase(expected))
            return null;
        String extensions = requestHeaders.get("Sec-WebSocket-Extensions");
        boolean deflate = false;
        if (extensions != null) {
            if (extensions.equals("x-webkit-deflate-frame"))
                deflate = true;
            // is this right? do we want to crap out here? Commenting out
            // as I suspect this caused a regression.
//            else
//                return null;
        }

        WebSocketImpl ret = new WebSocketImpl(response.detachSocket());
        ret.setupParser(true, deflate);
        return ret;
    }
    
    HybiParser mParser;

    @Override
    public void close() {
        mSocket.close();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mSocket.setClosedCallback(handler);
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mSocket.getClosedCallback();
    }

    CompletedCallback mExceptionCallback;
    @Override
    public void setEndCallback(CompletedCallback callback) {
        mExceptionCallback = callback;
    }

    @Override
    public CompletedCallback getEndCallback() {
        return mExceptionCallback;
    }

    @Override
    public void send(byte[] bytes) {
        mSink.write(ByteBuffer.wrap(mParser.frame(bytes)));
    }
    
    @Override
    public void send(byte[] bytes, int offset, int len) {
    	mSink.write(ByteBuffer.wrap(mParser.frame(bytes, offset, len)));
    }

    @Override
    public void send(String string) {
        mSink.write(ByteBuffer.wrap(mParser.frame(string)));
    }

    private StringCallback mStringCallback;
    @Override
    public void setStringCallback(StringCallback callback) {
        mStringCallback = callback;
    }

    private DataCallback mDataCallback;
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
    
    @Override
    public boolean isBuffering() {
        return mSink.remaining() > 0;
    }

    @Override
    public void write(ByteBuffer bb) {
        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        bb.position(0);
        bb.limit(0);
        send(buf);
    }

    @Override
    public void write(ByteBufferList bb) {
        byte[] buf = bb.getAllByteArray();
        send(buf);
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mSink.setWriteableCallback(handler);
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mSink.getWriteableCallback();
    }
    
    @Override
    public AsyncSocket getSocket() {
        return mSocket;
    }

    @Override
    public AsyncServer getServer() {
        return mSocket.getServer();
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public void pause() {
        mSocket.pause();
    }

    @Override
    public void resume() {
        mSocket.resume();
    }

    @Override
    public boolean isPaused() {
        return mSocket.isPaused();
    }
}
