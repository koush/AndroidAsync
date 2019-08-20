package com.koushikdutta.async.future;


import java.util.concurrent.Executor;

public interface Future<T> extends Cancellable, java.util.concurrent.Future<T> {
    /**
     * Set a callback to be invoked when this Future completes.
     * @param callback
     * @return
     */
    void setCallback(FutureCallback<T> callback);

    /**
     * Set a callback to be invoked when the Future completes
     * with an error or a result.
     * The existing error or result will be passed down the chain, or a new error
     * may be thrown.
     * @param done
     * @return
     */
    Future<T> done(DoneCallback<T> done);

    /**
     * Set a callback to be invoked when this Future completes successfully.
     * @param callback
     * @return A future that will resolve once the success callback completes,
     * which may contain any errors thrown by the success callback.
     */
    Future<T> success(SuccessCallback<T> callback);

    /**
     * Set a callback to be invoked when this Future completes successfully.
     * @param then
     * @param <R>
     * @return A future containing all exceptions that happened prior or during
     * the callback, or the successful result.
     */
    <R> Future<R> then(ThenFutureCallback<R, T> then);

    /**
     * Set a callback to be invoked when this Future completes successfully.
     * @param then
     * @param <R>
     * @return A future containing all exceptions that happened prior or during
     * the callback, or the successful result.
     */
    <R> Future<R> thenConvert(ThenCallback<R, T> then);

    /**
     * Set a callback to be invoked when this future completes with a failure.
     * The failure can be observered and rethrown, otherwise it is considered handled.
     * The exception will be nulled for subsequent callbacks in the chain.
     * @param fail
     * @return
     */
    Future<T> fail(FailCallback fail);

    /**
     * Set a callback to be invoked when this future completes with a failure.
     * The failure can be observered and rethrown, or handled by returning
     * a new fallback value of the same type.
     * @param fail
     * @return
     */
    Future<T> failConvert(FailConvertCallback<T> fail);

    /**
     * Set a callback to be invoked when this future completes with a failure.
     * The failure should be observered and rethrown, or handled by returning
     * a new future of the same type.
     * @param fail
     * @return
     */
    Future<T> failRecover(FailRecoverCallback<T> fail);

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

    /**
     * Get the result on the executor thread.
     * @param executor
     * @return
     */
    default Future<T> executorThread(Executor executor) {
        SimpleFuture<T> ret = new SimpleFuture<>();
        executor.execute(() -> ret.setComplete(Future.this));
        return ret;
    }
}
