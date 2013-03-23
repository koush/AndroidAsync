package com.koushikdutta.async.http;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.LineEmitter.StringCallback;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

abstract class AsyncHttpResponseImpl extends FilteredDataEmitter implements AsyncHttpResponse {
    private AsyncHttpRequestBody mWriter;
    
    public AsyncSocket getSocket() {
        return mSocket;
    }
    
    void setSocket(AsyncSocket exchange) {
        mSocket = exchange;
        
        if (mSocket == null)
            return;

        mWriter = mRequest.getBody();
        if (mWriter != null) {
            mRequest.getHeaders().setContentType(mWriter.getContentType());
            if (mWriter.length() != -1) {
                mRequest.getHeaders().setContentLength(mWriter.length());
                mSink = mSocket;
            }
            else {
                mRequest.getHeaders().getHeaders().set("Transfer-Encoding", "Chunked");
                mSink = new ChunkedOutputFilter(mSocket);
            }
        }
        else {
            mSink = mSocket;
        }
        
        String rs = mRequest.getRequestString();
        com.koushikdutta.async.Util.writeAll(exchange, rs.getBytes(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (mWriter != null)
                    mWriter.write(mRequest, AsyncHttpResponseImpl.this);
            }
        });
        
        LineEmitter liner = new LineEmitter();
        exchange.setDataCallback(liner);
        liner.setLineCallback(mHeaderCallback);
        
        mSocket.setEndCallback(mReporter);
        mSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                // TODO: do we care? throw if socket is still writing or something?
            }
        });
    }
    
    private CompletedCallback mReporter = new CompletedCallback() {
        @Override
        public void onCompleted(Exception error) {
            if (error != null && !mCompleted) {
                report(new Exception("connection closed before response completed."));
            }
            else {
                report(error);
            }
        }
    };
    
    protected abstract void onHeadersReceived();
    
    StringCallback mHeaderCallback = new StringCallback() {
        private RawHeaders mRawHeaders = new RawHeaders();
        @Override
        public void onStringAvailable(String s) {
            try {
                if (mRawHeaders.getStatusLine() == null) {
                    mRawHeaders.setStatusLine(s);
                }
                else if (!"\r".equals(s)) {
                    mRawHeaders.addLine(s);
                }
                else {
                    mHeaders = new ResponseHeaders(mRequest.getUri(), mRawHeaders);
                    onHeadersReceived();
                    // socket may get detached after headers (websocket)
                    if (mSocket == null)
                        return;
                    DataEmitter emitter = Util.getBodyDecoder(mSocket, mRawHeaders, false, mReporter);
                    setDataEmitter(emitter);
                }
            }
            catch (Exception ex) {
                report(ex);
            }
        }
    };

    @Override
    protected void report(Exception e) {
        super.report(e);

        // DISCONNECT. EVERYTHING.
        // should not get any data after this point...
        // if so, eat it and disconnect.
        mSocket.setDataCallback(new NullDataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                super.onDataAvailable(emitter, bb);
                mSocket.close();
            }
        });
        mSocket.setWriteableCallback(null);
        mSocket.setClosedCallback(null);
        mSocket.setEndCallback(null);
        mCompleted = true;
    }
    
    private AsyncHttpRequest mRequest;
    private AsyncSocket mSocket;
    ResponseHeaders mHeaders;
    public AsyncHttpResponseImpl(AsyncHttpRequest request) {
        mRequest = request;
    }

    boolean mCompleted = false;

    @Override
    public ResponseHeaders getHeaders() {
        return mHeaders;
    }

    private boolean mFirstWrite = true;
    private void assertContent() {
        if (!mFirstWrite)
            return;
        mFirstWrite = false;
        Assert.assertNotNull(mRequest.getHeaders().getHeaders().get("Content-Type"));
        Assert.assertTrue(mRequest.getHeaders().getHeaders().get("Transfer-Encoding") != null || mRequest.getHeaders().getContentLength() != -1); 
    }

    DataSink mSink;

    @Override
    public void write(ByteBuffer bb) {
        assertContent();
        mSink.write(bb);
    }

    @Override
    public void write(ByteBufferList bb) {
        assertContent();
        mSink.write(bb);
    }

    @Override
    public void end() {
        write(ByteBuffer.wrap(new byte[0]));
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
    public boolean isOpen() {
        return mSink.isOpen();
    }

    @Override
    public void close() {
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
