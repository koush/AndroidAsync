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
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;

import java.io.IOException;
import java.nio.charset.Charset;

abstract class AsyncHttpResponseImpl extends FilteredDataEmitter implements AsyncSocket, AsyncHttpResponse, AsyncHttpClientMiddleware.ResponseHead {
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

        mSocket.setEndCallback(mReporter);

        mWriter = mRequest.getBody();

        LineEmitter liner = new LineEmitter();
        exchange.setDataCallback(liner);
        liner.setLineCallback(mHeaderCallback);
    }

    protected void onHeadersSent() {
        if (mWriter != null) {
            mWriter.write(mRequest, AsyncHttpResponseImpl.this, new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    onRequestCompleted(ex);
                }
            });
        } else {
            onRequestCompleted(null);
        }
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

    protected void onHeadersReceived() {
    }

    StringCallback mHeaderCallback = new StringCallback() {
        private Headers mRawHeaders = new Headers();
        private String statusLine;
        @Override
        public void onStringAvailable(String s) {
            try {
                if (statusLine == null) {
                    statusLine = s;
                }
                else if (!"\r".equals(s)) {
                    mRawHeaders.addLine(s);
                }
                else {
                    String[] parts = statusLine.split(" ", 3);
                    if (parts.length != 3)
                        throw new Exception(new IOException("Not HTTP"));

                    protocol = parts[0];
                    code = Integer.parseInt(parts[1]);
                    message = parts[2];
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
                        emitter = HttpUtil.getBodyDecoder(mSocket, Protocol.get(protocol), mHeaders, false);
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
    protected Headers mHeaders;
    public AsyncHttpResponseImpl(AsyncHttpRequest request) {
        mRequest = request;
    }

    boolean mCompleted = false;

    @Override
    public Headers headers() {
        return mHeaders;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead headers(Headers headers) {
        mHeaders = headers;
        return this;
    }

    int code;
    @Override
    public int code() {
        return code;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead code(int code) {
        this.code = code;
        return this;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead message(String message) {
        this.message = message;
        return this;
    }

    String protocol;
    @Override
    public String protocol() {
        return protocol;
    }

    String message;
    @Override
    public String message() {
        return message;
    }

    @Override
    public String toString() {
        if (mHeaders == null)
            return super.toString();
        return mHeaders.toPrefixString(protocol + " " + code + " " + message);
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
    public DataSink sink() {
        return mSink;
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead sink(DataSink sink) {
        mSink = sink;
        return this;
    }

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
        Multimap mm = Multimap.parseSemicolonDelimited(headers().get("Content-Type"));
        String cs;
        if (mm != null && null != (cs = mm.getString("charset")) && Charset.isSupported(cs)) {
            return cs;
        }
        return null;
    }
}
