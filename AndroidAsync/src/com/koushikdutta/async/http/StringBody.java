package com.koushikdutta.async.http;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;

public class StringBody implements AsyncHttpRequestBody<String> {
    public StringBody() {
    }

    byte[] mBodyBytes;
    String string;
    public StringBody(String string) {
        this();
        this.string = string;
    }

    private ByteBufferList data = null;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (data == null)
            data = new ByteBufferList();
        data.add(bb);
        bb.clear();
    }

    @Override
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink) {
        Util.writeAll(sink, mBodyBytes, new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
            }
        });
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

    @Override
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override
    public int length() {
        mBodyBytes = string.getBytes();
        return mBodyBytes.length;
    }

    @Override
    public String toString() {
        if (string == null && data != null) {
            string = data.readString();
            data = null;
        }
        return string;
    }

    @Override
    public String getBody() {
        return toString();
    }
}
