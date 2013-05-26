package com.koushikdutta.async.future;


public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    public Future<T> setCallback(FutureCallback<T> callback);
}
