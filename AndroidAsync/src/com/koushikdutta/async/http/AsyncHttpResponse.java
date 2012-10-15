package com.koushikdutta.async.http;

import com.koushikdutta.async.ExceptionEmitter;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public interface AsyncHttpResponse extends DataEmitter, ExceptionEmitter {
    public void setCompletedCallback(CompletedCallback handler);
    public CompletedCallback getCompletedCallback();
    public ResponseHeaders getHeaders();
}
