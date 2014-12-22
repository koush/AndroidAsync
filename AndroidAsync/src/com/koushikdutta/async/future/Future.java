package com.koushikdutta.async.future;


public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    /**
     * Set a callback to be invoked when this Future completes.
     * @param callback
     * @return
     */
    public Future<T> setCallback(FutureCallback<T> callback);

    /**
     * Set a callback to be invoked when this Future completes.
     * @param callback
     * @param <C>
     * @return The callback
     */
    public <C extends FutureCallback<T>> C then(C callback);

    /**
     * Get the result, if any. Returns null if still in progress.
     * @return
     */
    public T tryGet();

    /**
     * Get the exception, if any. Returns null if still in progress.
     * @return
     */
    public Exception tryGetException();
}
