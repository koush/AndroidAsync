package com.koushikdutta.async.http;

import com.koushikdutta.async.ExceptionEmitter;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;

public interface AsyncHttpResponse extends DataEmitter, ExceptionEmitter {
    public void setCompletedCallback(CompletedCallback handler);
    public CompletedCallback getCloseHandler();
}
