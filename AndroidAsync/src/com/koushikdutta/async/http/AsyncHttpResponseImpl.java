package com.koushikdutta.async.http;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataCallback;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.LineEmitter.StringCallback;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

abstract class AsyncHttpResponseImpl extends FilteredDataCallback implements AsyncHttpResponse {
    private RawHeaders mRawHeaders = new RawHeaders();
    RawHeaders getRawHeaders() {
        return mRawHeaders;
    }

    private AsyncHttpRequestBody mWriter;
    void setSocket(AsyncSocket exchange) {
        mSocket = exchange;

        mWriter = mRequest.getBody();
        if (mWriter != null) {
            mRequest.getHeaders().setContentType(mWriter.getContentType());
            mRequest.getHeaders().getHeaders().set("Transfer-Encoding", "Chunked");
        }
        
        String rs = mRequest.getRequestString();
        com.koushikdutta.async.Util.writeAll(exchange, rs.getBytes(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (mWriter != null)
                    mWriter.write(mRequest, AsyncHttpResponseImpl.this);
            }
        });
        
        LineEmitter liner = new LineEmitter(exchange);
        liner.setLineCallback(mHeaderCallback);
        
        mSocket.setCompletedCallback(mReporter);
        mSocket.setClosedCallback(new ClosedCallback() {
            @Override
            public void onClosed() {
                if (!mCompleted) {
                    report(new Exception("connection closed before response completed."));
                }
            }
        });
    }
    
    private CompletedCallback mReporter = new CompletedCallback() {
        @Override
        public void onCompleted(Exception error) {
            report(error);
        }
    };
    
    protected abstract void onHeadersReceived();
    
    StringCallback mHeaderCallback = new StringCallback() {
        @Override
        public void onStringAvailable(String s) {
            try {
                if (mRawHeaders.getStatusLine() == null) {
                    mRawHeaders.setStatusLine(s);
                }
                else if (!"\r".equals(s)){
                    mRawHeaders.addLine(s);
                }
                else {
                    mHeaders = new ResponseHeaders(mRequest.getUri(), mRawHeaders);
                    onHeadersReceived();
                    // socket may get detached after headers (websocket)
                    if (mSocket == null)
                        return;
                    DataCallback callback = Util.getBodyDecoder(AsyncHttpResponseImpl.this, mRawHeaders, mReporter);
                    mSocket.setDataCallback(callback);
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
        mSocket.setCompletedCallback(null);
        mCompleted = true;
//        System.out.println("closing up shop");
//        if (mCompletedCallback != null)
//            mCompletedCallback.onCompleted(e);
    }
    
    private AsyncHttpRequest mRequest;
    AsyncSocket mSocket;
    private ResponseHeaders mHeaders;
    public AsyncHttpResponseImpl(AsyncHttpRequest request) {
        mRequest = request;
    }

    boolean mCompleted = false;
//    
//    CompletedCallback mCompletedCallback;
//    @Override
//    public void setCompletedCallback(CompletedCallback handler) {
//        mCompletedCallback = handler;        
//    }
//
//    @Override
//    public CompletedCallback getCompletedCallback() {
//        return mCompletedCallback;
//    }

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

    ChunkedOutputFilter mChunker;
    void initChunker() {
        if (mChunker != null)
            return;
        mChunker = new ChunkedOutputFilter(mSocket);
    }

    @Override
    public void write(ByteBuffer bb) {
        assertContent();
        initChunker();
        mChunker.write(bb);
    }

    @Override
    public void write(ByteBufferList bb) {
        assertContent();
        initChunker();
        mChunker.write(bb);
    }

    @Override
    public void end() {
        write(ByteBuffer.wrap(new byte[0]));
    }


    @Override
    public void setWriteableCallback(WritableCallback handler) {
        initChunker();
        mChunker.setWriteableCallback(handler);
    }

    @Override
    public WritableCallback getWriteableCallback() {
        initChunker();
        return mChunker.getWriteableCallback();
    }
}
