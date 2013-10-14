package com.koushikdutta.async.http;

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
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

import java.nio.ByteBuffer;

abstract class AsyncHttpResponseImpl extends FilteredDataEmitter implements AsyncHttpResponse {
    private AsyncHttpRequestBody mWriter;
    
    public AsyncSocket getSocket() {
        return mSocket;
    }

    @Override
    public AsyncHttpRequest getRequest() {
        return mRequest;
    }

    void setSocket(AsyncSocket exchange) {
        mSocket = exchange;
        
        if (mSocket == null)
            return;

        mWriter = mRequest.getBody();
        if (mWriter != null) {
            if (mRequest.getHeaders().getContentType() == null)
                mRequest.getHeaders().setContentType(mWriter.getContentType());
            if (mWriter.length() > 0) {
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

        mSocket.setEndCallback(mReporter);
        mSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                // TODO: do we care? throw if socket is still writing or something?
            }
        });

        String rs = mRequest.getRequestString();
        mRequest.logv("\n" + rs);
        com.koushikdutta.async.Util.writeAll(exchange, rs.getBytes(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (mWriter != null) {
                    mWriter.write(mRequest, AsyncHttpResponseImpl.this, new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception ex) {
                            onRequestCompleted(ex);
                        }
                    });
                }
                else {
                    onRequestCompleted(null);
                }
            }
        });

        LineEmitter liner = new LineEmitter();
        exchange.setDataCallback(liner);
        liner.setLineCallback(mHeaderCallback);
    }

    protected void onRequestCompleted(Exception ex) {
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
                    DataEmitter emitter;
                    // HEAD requests must not return any data. They still may
                    // return content length, etc, which will confuse the body decoder
                    if (AsyncHttpHead.METHOD.equalsIgnoreCase(mRequest.getMethod())) {
                        emitter = HttpUtil.EndEmitter.create(getServer(), null);
                    }
                    else {
                        emitter = HttpUtil.getBodyDecoder(mSocket, mRawHeaders, false);
                    }
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
        assert null != mRequest.getHeaders().getHeaders().get("Content-Type");
        assert mRequest.getHeaders().getHeaders().get("Transfer-Encoding") != null || mRequest.getHeaders().getContentLength() != -1;
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
