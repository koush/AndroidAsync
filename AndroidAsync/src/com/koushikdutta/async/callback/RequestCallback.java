package com.koushikdutta.async.callback;

import com.koushikdutta.async.http.AsyncHttpResponse;

public interface RequestCallback<T> extends ResultCallback<AsyncHttpResponse, T> {
    public void onProgress(AsyncHttpResponse response, int downloaded, int total);
}
