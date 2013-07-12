package com.koushikdutta.async.http.server;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpRequestBody;

public class UnknownRequestBody implements AsyncHttpRequestBody<Void> {
    public UnknownRequestBody(String contentType) {
        mContentType = contentType;
    }

    @Override
    public void write(AsyncHttpRequest request, DataSink sink, final CompletedCallback completed) {
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

    @Override
    public void parse(DataEmitter emitter, CompletedCallback completed) {
        emitter.setEndCallback(completed);
        emitter.setDataCallback(new NullDataCallback());
    }
}
