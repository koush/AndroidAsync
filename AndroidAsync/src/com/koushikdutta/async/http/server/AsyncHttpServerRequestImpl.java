package com.koushikdutta.async.http.server;

import java.util.regex.Matcher;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.LineEmitter.StringCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.RequestHeaders;

public class AsyncHttpServerRequestImpl implements AsyncHttpServerRequest {
    private RawHeaders mRawHeaders = new RawHeaders();
    AsyncSocket mSocket;
    Matcher mMatcher;

    protected void report(Exception e) {
    }

    protected void onHeadersReceived() {
    }
    
    protected void onNotHttp() {
        
    }
    
    StringCallback mHeaderCallback = new StringCallback() {
        @Override
        public void onStringAvailable(String s) {
            try {
                if (mRawHeaders.getStatusLine() == null) {
                    mRawHeaders.setStatusLine(s);
                    if (!mRawHeaders.getStatusLine().contains("HTTP/")) {
                        onNotHttp();
                        mSocket.setDataCallback(null);
                    }
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

    RawHeaders getRawHeaders() {
        return mRawHeaders;
    }
    
    void setSocket(AsyncSocket socket) {
        mSocket = socket;
        
        
        LineEmitter liner = new LineEmitter(mSocket);
        liner.setLineCallback(mHeaderCallback);
        
    }

    private RequestHeaders mHeaders = new RequestHeaders(null, mRawHeaders);
    @Override
    public RequestHeaders getHeaders() {
        return mHeaders;
    }

    @Override
    public void setDataCallback(DataCallback callback) {
        mSocket.setDataCallback(callback);
    }

    @Override
    public DataCallback getDataCallback() {
        return mSocket.getDataCallback();
    }

    @Override
    public boolean isChunked() {
        return mSocket.isChunked();
    }

    @Override
    public Matcher getMatcher() {
        return mMatcher;
    }

}
