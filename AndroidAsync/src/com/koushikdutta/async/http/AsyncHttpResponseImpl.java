package com.koushikdutta.async.http;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataExchange;
import com.koushikdutta.async.DataTransformerBase;
import com.koushikdutta.async.ExceptionCallback;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.LineEmitter.StringCallback;
import com.koushikdutta.async.SSLDataExchange;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.ResponseHeaders;
import com.koushikdutta.async.http.transform.ChunkedTransformer;
import com.koushikdutta.async.http.transform.GZIPTransformer;
import com.koushikdutta.async.http.transform.InflaterTransformer;

public class AsyncHttpResponseImpl extends DataTransformerBase implements AsyncHttpResponse {
    private RawHeaders mRawHeaders = new RawHeaders();
    RawHeaders getRawHeaders() {
        return mRawHeaders;
    }

    void setSocket(AsyncSocket socket) {
        mSocket = socket;
        // socket and exchange are the same for regular http
        // but different for https (ssl)
        // the exchange will be a wrapper around socket that does
        // ssl translation.
        DataExchange exchange = socket;
        if (mRequest.getUri().getScheme().equals("https")) {
            SSLDataExchange ssl = new SSLDataExchange(socket);
            exchange = ssl;
            socket.setDataCallback(ssl);
        }
        mExchange = exchange;

        mWriter = new BufferedDataSink(exchange);
        String rs = mRequest.getRequestString();
        mWriter.write(ByteBuffer.wrap(rs.getBytes()));
        
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
    
    protected void onHeadersReceived() {
        mHeaders = new ResponseHeaders(mRequest.getUri(), mRawHeaders);

        DataCallback callback = this;
        
        if ("gzip".equals(mHeaders.getContentEncoding())) {
            GZIPTransformer gunzipper = new GZIPTransformer() {
                @Override
                public void onException(Exception error) {
                    report(error);
                }
            };
            gunzipper.setDataCallback(callback);
            callback = gunzipper;
        }        
        else if ("deflate".equals(mHeaders.getContentEncoding())) {
            InflaterTransformer inflater = new InflaterTransformer() {
                @Override
                public void onException(Exception error) {
                    report(error);
                }
            };
            inflater.setDataCallback(callback);
            callback = inflater;
        }

        if (!mHeaders.isChunked()) {
            if (mHeaders.getContentLength() < 0) {
                report(new Exception("not using chunked encoding, and no content-length found."));
                return;
            }
            DataTransformerBase contentLengthWatcher = new DataTransformerBase() {
                int totalRead = 0;
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    totalRead += bb.remaining();
                    Assert.assertTrue(totalRead <= mHeaders.getContentLength());
                    super.onDataAvailable(emitter, bb);
                    if (totalRead == mHeaders.getContentLength())
                        report(null);
                }
            };
            contentLengthWatcher.setDataCallback(callback);
            callback = contentLengthWatcher;
        }
        else {
            ChunkedTransformer chunker = new ChunkedTransformer() {
                @Override
                public void onCompleted(Exception ex) {
                    AsyncHttpResponseImpl.this.report(ex);
                }

                @Override
                public void onException(Exception error) {
                    report(error);
                }
            };
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

    void report(Exception ex) {
        if (ex != null) {
            if (mErrorCallback != null)
                mErrorCallback.onException(ex);
        }
        onCompleted(ex);
    }
    
    private boolean hasParsedStatusLine = false;
    private BufferedDataSink mWriter;
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
        mSocket.setClosedCallback(null);
        mSocket.setExceptionCallback(null);
        mSocket.setDataCallback(null);
        mSocket.setWriteableCallback(null);
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
    public CompletedCallback getCloseHandler() {
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
}
