package com.koushikdutta.async.callback;

import com.koushikdutta.async.http.AsyncHttpResponse;

public interface RequestCallback<T> {
    public void onCompleted(Exception e, AsyncHttpResponse response, T result);
}
