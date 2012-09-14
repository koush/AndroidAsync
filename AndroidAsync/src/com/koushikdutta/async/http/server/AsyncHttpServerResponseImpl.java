package com.koushikdutta.async.http.server;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import junit.framework.Assert;

import org.json.JSONObject;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public class AsyncHttpServerResponseImpl implements AsyncHttpServerResponse {
    private RawHeaders mRawHeaders = new RawHeaders();
    private int mContentLength = -1;
    private ResponseHeaders mHeaders = new ResponseHeaders(null, mRawHeaders);
    
    @Override
    public ResponseHeaders getHeaders() {
        return mHeaders;
    }
    
    AsyncSocket mSocket;
    BufferedDataSink mSink;
    AsyncHttpServerResponseImpl(AsyncSocket socket) {
        mSocket = socket;
        mSink = new BufferedDataSink(socket);
    }
    
    @Override
    public void write(ByteBuffer bb) {
        ByteBufferList list = new ByteBufferList();
        list.add(bb);
        write(list);
    }

    @Override
    public void write(ByteBufferList bb) {
        Assert.assertTrue(mContentLength < 0);
        if (null == mRawHeaders.get("Transfer-Encoding")) {
            Assert.assertNotNull(mRawHeaders.getStatusLine());
            mRawHeaders.set("Transfer-Encoding", "Chunked");
            writeHead();
        }
        String chunkLen = Integer.toString(bb.remaining(), 16) + "\r\n";
        bb.add(0, ByteBuffer.wrap(chunkLen.getBytes()));
        bb.add(ByteBuffer.wrap("\r\n".getBytes()));
        mSocket.write(bb);
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
    }

    @Override
    public WritableCallback getWriteableCallback() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void end() {
        if (null == mRawHeaders.get("Transfer-Encoding")) {
            send("text/html", "");
            onCompleted();
            return;
        }
        write(ByteBuffer.wrap(new byte[0]));
        onCompleted();
    }
    
    private boolean mHeadWritten = false;
    @Override
    public void writeHead() {
        Assert.assertFalse(mHeadWritten);
        mHeadWritten = true;
        mSink.write(ByteBuffer.wrap(mRawHeaders.toHeaderString().getBytes()));
    }
    
    private void send(String contentType, String string) {
        try {
            Assert.assertTrue(mContentLength < 0);
            byte[] bytes = string.getBytes("UTF-8");
            mContentLength = bytes.length;
            mRawHeaders.set("Content-Length", Integer.toString(bytes.length));
            mRawHeaders.set("Content-Type", contentType);
            
            writeHead();
            mSink.write(ByteBuffer.wrap(string.getBytes()));
        }
        catch (UnsupportedEncodingException e) {
            Assert.fail();
        }
    }
    
    protected void onCompleted() {
    }
    
    protected void report(Exception e) {
    }


    @Override
    public void send(String string) {
        responseCode(200);
        send("text/html", string);
    }

    @Override
    public void send(JSONObject json) {
        send("application/json", json.toString());
    }

    @Override
    public void responseCode(int code) {
        String status = AsyncHttpServer.getResponseCodeDescription(code);
        mRawHeaders.setStatusLine(String.format("HTTP/1.1 %d %s", code, status));
    }
}
