package com.koushikdutta.async.future;


import com.koushikdutta.async.callback.ValueCallback;

public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    /**
     * Set a callback to be invoked when this Future completes.
     * @param callback
     * @return
     */
    void setCallback(FutureCallback<T> callback);

    /**
     * Set a callback to be invoked when this Future completes successfully.
     * @param callback
     */
    Future<T> success(SuccessCallback<T> callback);

    /**
     * Set a callback to be invoked when this Future completes successfully.
     * successfully.
     * @param then
     * @param <R>
     * @return A future that will contain the future result of ThenCallback
     * or the failure from this Future.
     */
    <R> Future<R> then(ThenFutureCallback<R, T> then);

    /**
     * Set a callback to be invoked when this Future completes successfully.
     * successfully.
     * @param then
     * @param <R>
     * @return A future that will contain the result of ThenCallback
     * or the failure from this Future.
     */
    <R> Future<R> thenConvert(ThenCallback<R, T> then);

    /**
     * Set a callback to be invoked when this future completes with a failure.
     * The failure can be observered and rethrown, or handled by returning
     * a new value of the same type.
     * @param fail
     * @return
     */
    Future<T> failConvert(FailCallback<T> fail);

    /**
     * Set a callback to be invoked when this future completes with a failure.
     * The failure should be observered and rethrown, or handled by returning
     * a new future of the same type.
     * @param fail
     * @return
     */
    Future<T> fail(FailFutureCallback<T> fail);

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
