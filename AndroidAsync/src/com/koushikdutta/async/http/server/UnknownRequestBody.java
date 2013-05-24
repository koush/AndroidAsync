package com.koushikdutta.async.http.server;

import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpRequestBody;
import com.koushikdutta.async.http.AsyncHttpResponse;

public class UnknownRequestBody extends NullDataCallback implements AsyncHttpRequestBody<Void> {
    public UnknownRequestBody(String contentType) {
        mContentType = contentType;
    }

    @Override
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink) {
        assert false;
    }

    private String mContentType;
    @Override
    public String getContentType() {
        return mContentType;
    }

    @Override
    public boolean readFullyOnRequest() {
        return false;
    }

    @Override
    public int length() {
        return -1;
    }

    @Override
    public Void get() {
        return null;
    }
}
