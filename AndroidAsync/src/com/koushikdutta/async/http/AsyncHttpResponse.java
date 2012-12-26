package com.koushikdutta.async.http;

import com.koushikdutta.async.DataExchange;
import com.koushikdutta.async.CompletedEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public interface AsyncHttpResponse extends DataExchange, CompletedEmitter {
    public void setCompletedCallback(CompletedCallback handler);
    public CompletedCallback getCompletedCallback();
    public ResponseHeaders getHeaders();
    public void end();
}
