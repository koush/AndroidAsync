package com.koushikdutta.async.http.server;

import junit.framework.Assert;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpRequestBody;
import com.koushikdutta.async.http.AsyncHttpResponse;

public class UnknownRequestBody extends FilteredDataCallback implements AsyncHttpRequestBody {
    public UnknownRequestBody(DataEmitter emitter, String contentType) {
        mContentType = contentType;
        emitter.setDataCallback(this);
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
    }
}
