package com.koushikdutta.async.http.callback;


import com.koushikdutta.async.http.AsyncHttpResponse;

public interface HttpConnectCallback {
    public void onConnectCompleted(Exception ex, AsyncHttpResponse response);
}
