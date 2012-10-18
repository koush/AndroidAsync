package com.koushikdutta.async.http;

import com.koushikdutta.async.DataSink;

public abstract class AsyncHttpRequestContentWriter {
    public abstract void write(DataSink sink);
}
