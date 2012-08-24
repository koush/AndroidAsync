package com.koushikdutta.async.callback;

public interface ResultCallback<T> {
    public void onCompleted(Exception e, T result);
}
