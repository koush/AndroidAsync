package com.koushikdutta.async.future;


public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    public FutureCallback<T> getResultCallback();
    public Future<T> setResultCallback(FutureCallback<T> callback);
}
