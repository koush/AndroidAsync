package com.koushikdutta.async.callback;

import com.koushikdutta.async.http.AsyncHttpResponse;

public interface RequestCallback<T> extends ResultCallback<T> {
    public void onConnect(AsyncHttpResponse response);
    public void onProgress(AsyncHttpResponse response, int downloaded, int total);
}
