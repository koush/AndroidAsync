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
import com.koushikdutta.async.http.cache.RawHeaders;

import java.nio.charset.Charset;

abstract class AsyncHttpResponseImpl extends FilteredDataEmitter implements AsyncSocket, AsyncHttpResponse {
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
            if (mRequest.getHeaders().get("Content-Type") == null)
                mRequest.getHeaders().set("Content-Type", mWriter.getContentType());
            if (mWriter.length() >= 0) {
                mRequest.getHeaders().set("Content-Length", String.valueOf(mWriter.length()));
                mSink = mSocket;
            }
            else {
                mRequest.getHeaders().set("Transfer-Encoding", "Chunked");
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
                report(new ConnectionClosedException("connection closed before response completed.", error));
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
                    mHeaders = mRawHeaders;
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
    protected RawHeaders mHeaders;
    public AsyncHttpResponseImpl(AsyncHttpRequest request) {
        mRequest = request;
    }

    boolean mCompleted = false;

    @Override
    public RawHeaders headers() {
        return mHeaders;
    }

    @Override
    public int code() {
        return headers().getResponseCode();
    }

    @Override
    public String protocol() {
        return "HTTP/1." + headers().getHttpMinorVersion();
    }

    @Override
    public String message() {
        return headers().getResponseMessage();
    }

    private boolean mFirstWrite = true;
    private void assertContent() {
        if (!mFirstWrite)
            return;
        mFirstWrite = false;
        assert null != mRequest.getHeaders().get("Content-Type");
        assert mRequest.getHeaders().get("Transfer-Encoding") != null || HttpUtil.contentLength(mRequest.getHeaders()) != -1;
    }

    DataSink mSink;
    @Override
    public void write(ByteBufferList bb) {
        assertContent();
        mSink.write(bb);
    }

    @Override
    public void end() {
        if (mSink instanceof ChunkedOutputFilter)
            mSink.end();
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
    public String charset() {
        Multimap mm = Multimap.parseHeader(headers(), "Content-Type");
        String cs;
        if (mm != null && null != (cs = mm.getString("charset")) && Charset.isSupported(cs)) {
            return cs;
        }
        return null;
    }
}
