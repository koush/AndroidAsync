package com.koushikdutta.async.http;

import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.DataParser;

public interface AsyncHttpRequestBody<T> extends DataParser<T> {
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink);
    public String getContentType();
    public boolean readFullyOnRequest();
    public int length();
}
