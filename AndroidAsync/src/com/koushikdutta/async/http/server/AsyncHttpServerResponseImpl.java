package com.koushikdutta.async.http.server;

import com.koushikdutta.async.*;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class AsyncHttpServerResponseImpl implements AsyncHttpServerResponse {
    private RawHeaders mRawHeaders = new RawHeaders();
    private int mContentLength = -1;
    private ResponseHeaders mHeaders = new ResponseHeaders(null, mRawHeaders);
    
    @Override
    public ResponseHeaders getHeaders() {
        return mHeaders;
    }
    
    public AsyncSocket getSocket() {
        return mSocket;
    }

    AsyncSocket mSocket;
    BufferedDataSink mSink;
    AsyncHttpServerRequestImpl mRequest;
    AsyncHttpServerResponseImpl(AsyncSocket socket, AsyncHttpServerRequestImpl req) {
        mSocket = socket;
        mSink = new BufferedDataSink(socket);
        mRequest = req;
        mRawHeaders.set("Connection", "Keep-Alive");
    }
    
    @Override
    public void write(ByteBuffer bb) {
        if (bb.remaining() == 0)
            return;
        writeInternal(bb);
    }

    private void writeInternal(ByteBuffer bb) {
        initFirstWrite();
        mChunker.write(bb);
    }

    boolean mHasWritten = false;
    ChunkedOutputFilter mChunker;
    void initFirstWrite() {
        if (mHasWritten)
            return;

        assert mContentLength < 0;
        assert null != mRawHeaders.getStatusLine();
        mRawHeaders.set("Transfer-Encoding", "Chunked");
        writeHead();
        mSink.setMaxBuffer(0);
        mHasWritten = true;
        mChunker = new ChunkedOutputFilter(mSink);
    }

    private void writeInternal(ByteBufferList bb) {
        assert !mEnded;
        initFirstWrite();
        mChunker.write(bb);
    }

    @Override
    public void write(ByteBufferList bb) {
        if (bb.remaining() == 0)
            return;
        writeInternal(bb);
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
            onEnd();
            return;
        }
        initFirstWrite();
        
        mChunker.setMaxBuffer(Integer.MAX_VALUE);
        mChunker.write(new ByteBufferList());

        onEnd();
    }

    private boolean mHeadWritten = false;
    @Override
    public void writeHead() {
        assert !mHeadWritten;
        mHeadWritten = true;
        mSink.write(ByteBuffer.wrap(mRawHeaders.toHeaderString().getBytes()));
    }

    @Override
    public void setContentType(String contentType) {
        assert !mHeadWritten;
        mRawHeaders.set("Content-Type", contentType);
    }
    
    public void send(String contentType, String string) {
        try {
            if (mRawHeaders.getStatusLine() == null)
                responseCode(200);
            assert mContentLength < 0;
            byte[] bytes = string.getBytes("UTF-8");
            mContentLength = bytes.length;
            mRawHeaders.set("Content-Length", Integer.toString(bytes.length));
            mRawHeaders.set("Content-Type", contentType);

            writeHead();
            mSink.write(ByteBuffer.wrap(string.getBytes()));
            onEnd();
        }
        catch (UnsupportedEncodingException e) {
            assert false;
        }
    }
    
    boolean mEnded;
    protected void onEnd() {
        mEnded = true;
    }
    
    protected void report(Exception e) {
    }


    @Override
    public void send(String string) {
        responseCode(200);
        send("text/html; charset=utf8", string);
    }

    @Override
    public void send(JSONObject json) {
        send("application/json; charset=utf8", json.toString());
    }
    
    public void sendFile(File file) {
        try {
            FileInputStream fin = new FileInputStream(file);
            mRawHeaders.set("Content-Type", AsyncHttpServer.getContentType(file.getAbsolutePath()));
            responseCode(200);
            Util.pump(fin, this, new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    end();
                }
            });
        }
        catch (FileNotFoundException e) {
            responseCode(404);
            end();
        }
    }

    @Override
    public void responseCode(int code) {
        String status = AsyncHttpServer.getResponseCodeDescription(code);
        mRawHeaders.setStatusLine(String.format("HTTP/1.1 %d %s", code, status));
    }

    @Override
    public void redirect(String location) {
        responseCode(302);
        mRawHeaders.set("Location", location);
        end();
    }

    @Override
    public void onCompleted(Exception ex) {
        if (ex != null) {
            ex.printStackTrace();
        }
        end();
    }

    @Override
    public boolean isOpen() {
        return mSink.isOpen();
    }

    @Override
    public void close() {
        end();
        // if we're using the chunker, close that.
        // there may be data pending. That will eventually call
        // the close callback in the underlying mSink
        if (mChunker != null)
            mChunker.close();
        else
            mSink.close();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mSink.setClosedCallback(handler);
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mSink.getClosedCallback();
    }

    @Override
    public AsyncServer getServer() {
        return mSocket.getServer();
    }
}
