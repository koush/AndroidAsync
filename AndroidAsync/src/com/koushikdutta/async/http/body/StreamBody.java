package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;

import java.io.InputStream;

public class StreamBody implements AsyncHttpRequestBody<InputStream> {
    InputStream stream;
    int length;

    public StreamBody(InputStream stream, int length) {
        this.stream = stream;
        this.length = length;
    }

    @Override
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        Util.pump(stream, this.length, sink, completed);
    }

    @Override
    public void parse(DataEmitter emitter, CompletedCallback completed) {
        throw new AssertionError("not implemented");
    }

    public static final String CONTENT_TYPE = "application/binary";
    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean readFullyOnRequest() {
        throw new AssertionError("not implemented");
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public InputStream get() {
        return stream;
    }
}
