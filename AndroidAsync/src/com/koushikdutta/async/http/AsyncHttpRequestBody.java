package com.koushikdutta.async.http;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

public interface AsyncHttpRequestBody extends DataCallback, CompletedCallback {
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink);
    public String getContentType();
}
