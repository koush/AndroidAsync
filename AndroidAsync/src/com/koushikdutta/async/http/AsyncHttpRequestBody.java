package com.koushikdutta.async.http;

import com.koushikdutta.async.callback.DataCallback;

public interface AsyncHttpRequestBody extends DataCallback {
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink);
    public String getContentType();
    public boolean readFullyOnRequest();
    public int length();
}
