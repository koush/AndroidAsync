package com.koushikdutta.async.http.server;

import junit.framework.Assert;

import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpRequestBody;
import com.koushikdutta.async.http.AsyncHttpResponse;

public class AsyncHttpRequestBodyBase extends FilteredDataEmitter implements AsyncHttpRequestBody {
    public AsyncHttpRequestBodyBase(String contentType) {
        mContentType = contentType;
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

    @Override
    public void onCompleted(Exception ex) {
        report(ex);
    }

    @Override
    public boolean readFullyOnRequest() {
        return false;
    }

    @Override
    public int length() {
        return -1;
    }
}
