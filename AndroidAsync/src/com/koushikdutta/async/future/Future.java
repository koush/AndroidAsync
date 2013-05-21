package com.koushikdutta.async.future;


import com.koushikdutta.async.callback.ResultCallback;

public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    public ResultCallback<T> getResultCallback();
    public void setResultCallback(ResultCallback<T> callback);
}
