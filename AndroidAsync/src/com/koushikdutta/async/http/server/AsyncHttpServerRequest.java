package com.koushikdutta.async.http.server;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.libcore.RequestHeaders;

public interface AsyncHttpServerRequest extends DataEmitter {
    public RequestHeaders getHeaders();
}
