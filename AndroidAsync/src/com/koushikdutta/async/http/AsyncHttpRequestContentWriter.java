package com.koushikdutta.async.http;


public interface AsyncHttpRequestContentWriter {
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink);
    public String getContentType();
}
