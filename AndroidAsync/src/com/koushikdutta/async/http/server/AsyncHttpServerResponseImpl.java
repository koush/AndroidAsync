package com.koushikdutta.async.http.server;

import android.text.TextUtils;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.AsyncHttpHead;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import com.koushikdutta.async.util.StreamUtility;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class AsyncHttpServerResponseImpl implements AsyncHttpServerResponse {
    private Headers mRawHeaders = new Headers();
    private long mContentLength = -1;

    @Override
    public Headers getHeaders() {
        return mRawHeaders;
    }
    
    public AsyncSocket getSocket() {
        return mSocket;
    }

    AsyncSocket mSocket;
    AsyncHttpServerRequestImpl mRequest;
    AsyncHttpServerResponseImpl(AsyncSocket socket, AsyncHttpServerRequestImpl req) {
        mSocket = socket;
        mRequest = req;
        if (HttpUtil.isKeepAlive(Protocol.HTTP_1_1, req.getHeaders()))
            mRawHeaders.set("Connection", "Keep-Alive");
    }

    @Override
    public void write(ByteBufferList bb) {
        if (bb.remaining() == 0)
            return;
        writeInternal(bb);
    }

    private void writeInternal(ByteBufferList bb) {
        assert !mEnded;
        if (!mHasWritten) {
            initFirstWrite();
            return;
        }
        mSink.write(bb);
    }

    boolean mHasWritten = false;
    DataSink mSink;
    void initFirstWrite() {
        if (mHasWritten)
            return;

        mHasWritten = true;
        String currentEncoding = mRawHeaders.get("Transfer-Encoding");
        if ("".equals(currentEncoding))
            mRawHeaders.removeAll("Transfer-Encoding");
        boolean canUseChunked = ("Chunked".equalsIgnoreCase(currentEncoding) || currentEncoding == null)
           && !"close".equalsIgnoreCase(mRawHeaders.get("Connection"));
        if (mContentLength < 0) {
            String contentLength = mRawHeaders.get("Content-Length");
            if (!TextUtils.isEmpty(contentLength))
                mContentLength = Long.valueOf(contentLength);
        }
        if (mContentLength < 0 && canUseChunked) {
            mRawHeaders.set("Transfer-Encoding", "Chunked");
            mSink = new ChunkedOutputFilter(mSocket);
        }
        else {
            mSink = mSocket;
        }
        writeHeadInternal();
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        initFirstWrite();
        mSink.setWriteableCallback(handler);
    }

    @Override
    public WritableCallback getWriteableCallback() {
        initFirstWrite();
        return mSink.getWriteableCallback();
    }

    @Override
    public void end() {
        if ("Chunked".equalsIgnoreCase(mRawHeaders.get("Transfer-Encoding"))) {
            initFirstWrite();
            ((ChunkedOutputFilter)mSink).setMaxBuffer(Integer.MAX_VALUE);
            mSink.write(new ByteBufferList());
            onEnd();
        }
        else if (!mHasWritten) {
            if (!mRequest.getMethod().equalsIgnoreCase(AsyncHttpHead.METHOD))
                send("text/html", "");
            else {
                writeHead();
                onEnd();
            }
        }
    }

    private boolean mHeadWritten = false;
    @Override
    public void writeHead() {
        initFirstWrite();
    }

    private void writeHeadInternal() {
        assert !mHeadWritten;
        mHeadWritten = true;
        String statusLine = String.format("HTTP/1.1 %s %s", code, AsyncHttpServer.getResponseCodeDescription(code));
        String rh = mRawHeaders.toPrefixString(statusLine);
        Util.writeAll(mSocket, rh.getBytes(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                // TODO: HACK!!!
                // this really needs to be fixed. Not sure how to deal w/ writehead and
                // first write
                if (mSink instanceof BufferedDataSink)
                    ((BufferedDataSink)mSink).setDataSink(mSocket);
                WritableCallback writableCallback = getWriteableCallback();
                if (writableCallback != null)
                    writableCallback.onWriteable();
            }
        });
    }

    @Override
    public void setContentType(String contentType) {
        assert !mHeadWritten;
        mRawHeaders.set("Content-Type", contentType);
    }

    @Override
    public void send(String contentType, final String string) {
        try {
            assert mContentLength < 0;
            byte[] bytes = string.getBytes("UTF-8");
            mContentLength = bytes.length;
            mRawHeaders.set("Content-Length", Integer.toString(bytes.length));
            mRawHeaders.set("Content-Type", contentType);

            Util.writeAll(this, string.getBytes(), new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    onEnd();
                }
            });
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
        String contentType = mRawHeaders.get("Content-Type");
        if (contentType == null)
            contentType = "text/html; charset=utf8";
        send(contentType, string);
    }

    @Override
    public void send(JSONObject json) {
        send("application/json; charset=utf8", json.toString());
    }

    @Override
    public void sendStream(final InputStream inputStream, long totalLength) {
        long start = 0;
        long end = totalLength - 1;

        String range = mRequest.getHeaders().get("Range");
        if (range != null) {
            String[] parts = range.split("=");
            if (parts.length != 2 || !"bytes".equals(parts[0])) {
                // Requested range not satisfiable
                code(416);
                end();
                return;
            }

            parts = parts[1].split("-");
            try {
                if (parts.length > 2)
                    throw new MalformedRangeException();
                if (!TextUtils.isEmpty(parts[0]))
                    start = Long.parseLong(parts[0]);
                if (parts.length == 2 && !TextUtils.isEmpty(parts[1]))
                    end = Long.parseLong(parts[1]);
                else
                    end = totalLength - 1;

                code(206);
                getHeaders().set("Content-Range", String.format("bytes %d-%d/%d", start, end, totalLength));
            }
            catch (Exception e) {
                code(416);
                end();
                return;
            }
        }
        try {
            if (start != inputStream.skip(start))
                throw new StreamSkipException("skip failed to skip requested amount");
            mContentLength = end - start + 1;
            mRawHeaders.set("Content-Length", String.valueOf(mContentLength));
            mRawHeaders.set("Accept-Ranges", "bytes");
            if (mRequest.getMethod().equals(AsyncHttpHead.METHOD)) {
                writeHead();
                onEnd();
                return;
            }
            Util.pump(inputStream, mContentLength, this, new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    StreamUtility.closeQuietly(inputStream);
                    onEnd();
                }
            });
        }
        catch (Exception e) {
            code(500);
            end();
        }
    }

    @Override
    public void sendFile(File file) {
        try {
            if (mRawHeaders.get("Content-Type") == null)
                mRawHeaders.set("Content-Type", AsyncHttpServer.getContentType(file.getAbsolutePath()));
            FileInputStream fin = new FileInputStream(file);
            sendStream(new BufferedInputStream(fin, 64000), file.length());
        }
        catch (FileNotFoundException e) {
            code(404);
            end();
        }
    }

    int code = 200;
    @Override
    public AsyncHttpServerResponse code(int code) {
        this.code = code;
        return this;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public void redirect(String location) {
        code(302);
        mRawHeaders.set("Location", location);
        end();
    }

    @Override
    public void onCompleted(Exception ex) {
        end();
    }

    @Override
    public boolean isOpen() {
        if (mSink != null)
            return mSink.isOpen();
        return mSocket.isOpen();
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

    @Override
    public String toString() {
        if (mRawHeaders == null)
            return super.toString();
        String statusLine = String.format("HTTP/1.1 %s %s", code, AsyncHttpServer.getResponseCodeDescription(code));
        return mRawHeaders.toPrefixString(statusLine);
    }
}
