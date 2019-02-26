package com.koushikdutta.async.http.body;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.parser.ByteBufferListParser;

public class ByteBufferListRequestBody implements AsyncHttpRequestBody<ByteBufferList> {
    public ByteBufferListRequestBody() {
    }

    ByteBufferList bb;
    public ByteBufferListRequestBody(ByteBufferList bb) {
        this.bb = bb;
    }
    @Override
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        Util.writeAll(sink, bb, completed);
    }

    @Override
    public void parse(DataEmitter emitter, CompletedCallback completed) {
        new ByteBufferListParser().parse(emitter).setCallback((e, result) -> {
            bb = result;
            completed.onCompleted(e);
        });
    }

    public static String CONTENT_TYPE = "application/binary";

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override
    public int length() {
        return bb.remaining();
    }

    @Override
    public ByteBufferList get() {
        return bb;
    }
}
