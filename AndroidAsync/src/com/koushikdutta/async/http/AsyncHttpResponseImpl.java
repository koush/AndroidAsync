package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;

import java.nio.charset.Charset;

abstract class AsyncHttpResponseImpl extends FilteredDataEmitter implements DataEmitter, AsyncHttpResponse, AsyncHttpClientMiddleware.ResponseHead {
    public AsyncSocket socket() {
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
    }

    protected void onHeadersSent() {
        AsyncHttpRequestBody requestBody = mRequest.getBody();
        if (requestBody != null) {
            requestBody.write(mRequest, mSink, new CompletedCallback() {
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
            if (headers() == null) {
                report(new ConnectionClosedException("connection closed before headers received.", error));
            }
            else if (error != null && !mCompleted) {
                report(new ConnectionClosedException("connection closed before response completed.", error));
            }
            else {
                report(error);
            }
        }
    };

    protected void onHeadersReceived() {
    }


    @Override
    public DataEmitter emitter() {
        return getDataEmitter();
    }

    @Override
    public AsyncHttpClientMiddleware.ResponseHead emitter(DataEmitter emitter) {
        setDataEmitter(emitter);
        return this;
    }

    private void terminate() {
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
    }

    @Override
    protected void report(Exception e) {
        super.report(e);

        terminate();
        mSocket.setWriteableCallback(null);
        mSocket.setClosedCallback(null);
        mSocket.setEndCallback(null);
        mCompleted = true;
    }

    @Override
    public void close() {
        super.close();
        terminate();
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
