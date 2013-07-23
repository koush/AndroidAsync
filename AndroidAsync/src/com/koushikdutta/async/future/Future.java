package com.koushikdutta.async.future;


public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    /**
     * Set a callback to be invoked when this Future completes.
     * @param callback
     * @return
     */
    public Future<T> setCallback(FutureCallback<T> callback);
    public <C extends TransformFuture<?, T>> C then(C callback);
}
