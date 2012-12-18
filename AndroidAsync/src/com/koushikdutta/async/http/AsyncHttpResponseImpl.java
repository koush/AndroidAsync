package com.koushikdutta.async.http;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataExchange;
import com.koushikdutta.async.FilteredDataCallback;
import com.koushikdutta.async.ExceptionCallback;
import com.koushikdutta.async.FilteredDataSink;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.LineEmitter.StringCallback;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.filter.ChunkedInputFilter;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import com.koushikdutta.async.http.filter.GZIPInputFilter;
import com.koushikdutta.async.http.filter.InflaterInputFilter;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public class AsyncHttpResponseImpl extends FilteredDataCallback implements AsyncHttpResponse {
    private RawHeaders mRawHeaders = new RawHeaders();
    RawHeaders getRawHeaders() {
        return mRawHeaders;
    }

    private AsyncHttpRequestContentWriter mWriter;
    void setSocket(AsyncSocket socket, DataExchange exchange) {
        mSocket = socket;
        mExchange = exchange;

        mWriter = mRequest.getContentWriter();
        if (mWriter != null) {
            mRequest.getHeaders().setContentType(mWriter.getContentType());
            mRequest.getHeaders().getHeaders().set("Transfer-Encoding", "Chunked");
        }
        
        String rs = mRequest.getRequestString();
        Util.writeAll(exchange, rs.getBytes(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (mWriter != null)
                    mWriter.write(mRequest, AsyncHttpResponseImpl.this);
            }
        });
        
        LineEmitter liner = new LineEmitter(exchange);
        liner.setLineCallback(mHeaderCallback);
        
        mSocket.setExceptionCallback(new ExceptionCallback() {
            @Override
            public void onException(Exception error) {
                report(error);
            }
        });
        mSocket.setClosedCallback(new ClosedCallback() {
            @Override
            public void onClosed() {
                if (!mCompleted) {
                    report(new Exception("connection closed before response completed."));
                }
            }
        });
    }
    
    private ExceptionCallback mReporter = new ExceptionCallback() {
        @Override
        public void onException(Exception error) {
            report(error);
        }
    };
    
    protected void onHeadersReceived() {
        mHeaders = new ResponseHeaders(mRequest.getUri(), mRawHeaders);
        
        if (mHeaders.getContentLength() == 0) {
            report(null);
            return;
        }

        DataCallback callback = this;
        
        if ("gzip".equals(mHeaders.getContentEncoding())) {
            GZIPInputFilter gunzipper = new GZIPInputFilter();
            gunzipper.setDataCallback(callback);
            gunzipper.setExceptionCallback(mReporter);
            callback = gunzipper;
        }        
        else if ("deflate".equals(mHeaders.getContentEncoding())) {
            InflaterInputFilter inflater = new InflaterInputFilter();
            inflater.setExceptionCallback(mReporter);
            inflater.setDataCallback(callback);
            callback = inflater;
        }

        if (!mHeaders.isChunked()) {
            if (mHeaders.getContentLength() < 0) {
                report(new Exception("not using chunked encoding, and no content-length found."));
                return;
            }
            FilteredDataCallback contentLengthWatcher = new FilteredDataCallback() {
                int totalRead = 0;
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    totalRead += bb.remaining();
                    Assert.assertTrue(totalRead <= mHeaders.getContentLength());
                    super.onDataAvailable(emitter, bb);
                    if (totalRead == mHeaders.getContentLength())
                        AsyncHttpResponseImpl.this.report(null);
                }
            };
            contentLengthWatcher.setDataCallback(callback);
            callback = contentLengthWatcher;
        }
        else {
            ChunkedInputFilter chunker = new ChunkedInputFilter() {
                @Override
                public void onCompleted(Exception ex) {
                    AsyncHttpResponseImpl.this.report(ex);
                }
            };
            
            chunker.setExceptionCallback(mReporter);
            chunker.setDataCallback(callback);
            callback = chunker;
        }
        mExchange.setDataCallback(callback);
    }
    
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
                    onHeadersReceived();
//                    System.out.println(mRawHeaders.toHeaderString());
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
        onCompleted(e);
    }
    
    private AsyncSocket mSocket;
    private AsyncHttpRequest mRequest;
    private DataExchange mExchange;
    private ResponseHeaders mHeaders;
    public AsyncHttpResponseImpl(AsyncHttpRequest request) {
        mRequest = request;
    }

    boolean mCompleted = false;
    protected void onCompleted(Exception ex) {
        // DISCONNECT. EVERYTHING.
        // should not get any data after this point...
        // if so, eat it and disconnect.
        mExchange.setDataCallback(new NullDataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                super.onDataAvailable(emitter, bb);
                mSocket.close();
            }
        });
        mExchange.setWriteableCallback(null);
        mSocket.setClosedCallback(null);
        mSocket.setExceptionCallback(null);
        mCompleted = true;
//        System.out.println("closing up shop");
        if (mCompletedCallback != null)
            mCompletedCallback.onCompleted(ex);
    }
    
    CompletedCallback mCompletedCallback;
    @Override
    public void setCompletedCallback(CompletedCallback handler) {
        mCompletedCallback = handler;        
    }

    @Override
    public CompletedCallback getCompletedCallback() {
        return mCompletedCallback;
    }

    ExceptionCallback mErrorCallback;
    @Override
    public void setExceptionCallback(ExceptionCallback callback) {
        mErrorCallback = callback;
    }

    @Override
    public ExceptionCallback getExceptionCallback() {
        return mErrorCallback;
    }

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
