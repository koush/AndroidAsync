package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;

public interface AsyncHttpResponse extends DataEmitter {
    public void setEndCallback(CompletedCallback handler);
    public String protocol();
    public String message();
    public int code();
    public RawHeaders headers();
    public void end();
    public AsyncSocket detachSocket();
    public AsyncHttpRequest getRequest();
}
