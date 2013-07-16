package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public interface AsyncHttpResponse extends AsyncSocket {
    public void setEndCallback(CompletedCallback handler);
    public CompletedCallback getEndCallback();
    public ResponseHeaders getHeaders();
    public void end();
    public AsyncSocket detachSocket();
    public AsyncHttpRequest getRequest();
}
