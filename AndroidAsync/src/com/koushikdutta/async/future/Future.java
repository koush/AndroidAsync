package com.koushikdutta.async.future;


import android.view.ViewDebug;

public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    /**
     * Set a callback to be invoked when this Future completes.
     * @param callback
     * @return
     */
    Future<T> setCallback(FutureCallback<T> callback);

    /**
     * Set a callback to be invoked when this Future completes.
     * @param callback
     * @param <C>
     * @return The callback
     */
    @ViewDebug.ExportedProperty
    <C extends FutureCallback<T>> C then(C callback);

    /**
     * Set a callback to be invoked when this Future completes
     * successfully.
     * @param then
     * @param <R>
     * @return A future that will contain the future result of ThenCallback
     * or the failure from this Future.
     */
    <R> Future<R> then(ThenCallback<R, T> then);

    /**
     * Get the result, if any. Returns null if still in progress.
     * @return
     */
    T tryGet();

    /**
     * Get the exception, if any. Returns null if still in progress.
     * @return
     */
    Exception tryGetException();
}
