package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.CompletedEmitter;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public interface AsyncHttpResponse extends DataEmitter, DataSink, CompletedEmitter {
    public void setCompletedCallback(CompletedCallback handler);
    public CompletedCallback getCompletedCallback();
    public ResponseHeaders getHeaders();
    public void end();
    public AsyncSocket detachSocket();
}
