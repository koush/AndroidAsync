package com.koushikdutta.async.http.server;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import junit.framework.Assert;

import org.json.JSONObject;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.FilteredDataSink;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
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
        initFirstWrite();
        mChunker.write(bb);
    }

    boolean mHasWritten = false;
    FilteredDataSink mChunker;
    void initFirstWrite() {
        if (mHasWritten)
            return;

        Assert.assertTrue(mContentLength < 0);
        Assert.assertNotNull(mRawHeaders.getStatusLine());
        mRawHeaders.set("Transfer-Encoding", "Chunked");
        writeHead();
        mSink.setMaxBuffer(0);
        mHasWritten = true;
        mChunker = new ChunkedOutputFilter(mSink);
    }
    @Override
    public void write(ByteBufferList bb) {
        initFirstWrite();
        mChunker.write(bb);
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        initFirstWrite();
        mChunker.setWriteableCallback(handler);
    }

    @Override
    public WritableCallback getWriteableCallback() {
        initFirstWrite();
        return mChunker.getWriteableCallback();
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
    
    public void send(String contentType, String string) {
        try {
            if (mRawHeaders.getStatusLine() == null)
                responseCode(200);
            Assert.assertTrue(mContentLength < 0);
            byte[] bytes = string.getBytes("UTF-8");
            mContentLength = bytes.length;
            mRawHeaders.set("Content-Length", Integer.toString(bytes.length));
            mRawHeaders.set("Content-Type", contentType);
            
            writeHead();
            mSink.write(ByteBuffer.wrap(string.getBytes()));
            onCompleted();
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
