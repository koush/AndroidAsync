package com.koushikdutta.async.future;


import com.koushikdutta.async.callback.ResultCallback;

public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    public FutureCallback<T> getResultCallback();
    public void setResultCallback(FutureCallback<T> callback);
}
