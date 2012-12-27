package com.koushikdutta.async.http.server;

import junit.framework.Assert;

import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;

public class UnknownRequestBody extends AsyncHttpRequestBodyBase {
    public UnknownRequestBody(String contentType) {
        super(contentType);
        setDataCallback(new NullDataCallback());
    }

    @Override
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink) {
        Assert.fail();
    }

    private String mContentType;
    @Override
    public String getContentType() {
        return mContentType;
    }
}
