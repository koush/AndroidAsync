package com.koushikdutta.async.future;

import com.koushikdutta.async.AsyncSemaphore;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SimpleFuture<T> extends SimpleCancellable implements DependentFuture<T> {
    private AsyncSemaphore waiter;
    private Exception exception;
    private T result;
    boolean silent;
    FutureCallback<T> callback;
    FutureCallbackInternal internalCallback;

    private interface FutureCallbackInternal<T> {
        void onCompleted(Exception e, T result, FutureCallsite next);
    }

    public SimpleFuture() {
    }

    public SimpleFuture(T value) {
        setComplete(value);
    }

    public SimpleFuture(Exception e) {
        setComplete(e);
    }

    public SimpleFuture(Future<T> future) {
        setComplete(future);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    private boolean cancelInternal(boolean silent) {
        if (!super.cancel())
            return false;
        // still need to release any pending waiters
        FutureCallback<T> callback;
        FutureCallbackInternal<T> internalCallback;
        synchronized (this) {
            exception = new CancellationException();
            releaseWaiterLocked();
            callback = handleCompleteLocked();
            internalCallback = handleInternalCompleteLocked();
            this.silent = silent;
        }
        handleCallbackUnlocked(null, callback, internalCallback);
        return true;
    }

    public boolean cancelSilently() {
        return cancelInternal(true);
    }

    @Override
    public boolean cancel() {
        return cancelInternal(silent);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        AsyncSemaphore waiter;
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResultOrThrow();
            waiter = ensureWaiterLocked();
        }
        waiter.acquire();
        return getResultOrThrow();
    }

    private T getResultOrThrow() throws ExecutionException {
        if (exception != null)
            throw new ExecutionException(exception);
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        AsyncSemaphore waiter;
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResultOrThrow();
            waiter = ensureWaiterLocked();
        }
        if (!waiter.tryAcquire(timeout, unit))
            throw new TimeoutException();
        return getResultOrThrow();
    }

    @Override
    public boolean setComplete() {
        return setComplete((T)null);
    }

    private FutureCallback<T> handleCompleteLocked() {
        // don't execute the callback inside the sync block... possible hangup
        // read the callback value, and then call it outside the block.
        // can't simply call this.callback.onCompleted directly outside the block,
        // because that may result in a race condition where the callback changes once leaving
        // the block.
        FutureCallback<T> callback = this.callback;
        // null out members to allow garbage collection
        this.callback = null;
        return callback;
    }

    private FutureCallbackInternal<T> handleInternalCompleteLocked() {
        // don't execute the callback inside the sync block... possible hangup
        // read the callback value, and then call it outside the block.
        // can't simply call this.callback.onCompleted directly outside the block,
        // because that may result in a race condition where the callback changes once leaving
        // the block.
        FutureCallbackInternal<T> callback = this.internalCallback;
        // null out members to allow garbage collection
        this.callback = null;
        return callback;
    }

    static class FutureCallsite {
        Exception e;
        Object result;
        FutureCallbackInternal callback;

        void loop() {
            while (callback != null) {
                // these values always start non null.
                FutureCallbackInternal callback = this.callback;
                Exception e = this.e;
                Object result = this.result;

                // null them out for reentrancy
                this.callback = null;
                this.e = null;
                this.result = null;

                callback.onCompleted(e, result, this);
            }
        }
    }

    private void handleCallbackUnlocked(FutureCallsite callsite, FutureCallback<T> callback, FutureCallbackInternal<T> internalCallback) {
        if (silent)
            return;
        if (callback != null) {
            callback.onCompleted(exception, result);
            return;
        }

        if (internalCallback == null)
            return;

        boolean needsLoop = false;
        if (callsite == null) {
            needsLoop = true;
            callsite = new FutureCallsite();
        }

        callsite.callback = internalCallback;
        callsite.e = exception;
        callsite.result = result;

        if (needsLoop)
            callsite.loop();
    }

    void releaseWaiterLocked() {
        if (waiter != null) {
            waiter.release();
            waiter = null;
        }
    }

    AsyncSemaphore ensureWaiterLocked() {
        if (waiter == null)
            waiter = new AsyncSemaphore();
        return waiter;
    }

    public boolean setComplete(Exception e) {
        return setComplete(e, null, null);
    }

    public boolean setComplete(T value) {
        return setComplete(null, value, null);
    }

    public boolean setComplete(Exception e, T value) {
        return setComplete(e, value, null);
    }

    private boolean setComplete(Exception e, T value, FutureCallsite callsite) {
        FutureCallback<T> callback;
        FutureCallbackInternal internalCallback;
        synchronized (this) {
            if (!super.setComplete())
                return false;
            result = value;
            exception = e;
            releaseWaiterLocked();
            callback = handleCompleteLocked();
            internalCallback = handleInternalCompleteLocked();
        }
        handleCallbackUnlocked(callsite, callback, internalCallback);
        return true;
    }

    private void setCallbackInternal(FutureCallsite callsite, FutureCallback<T> callback, FutureCallbackInternal<T> internalCallback) {
        // callback can only be changed or read/used inside a sync block
        synchronized (this) {
            // done or cancelled,
            this.callback = callback;
            this.internalCallback = internalCallback;
            if (!isDone() && !isCancelled())
                return;

            callback = handleCompleteLocked();
            internalCallback = handleInternalCompleteLocked();
        }
        handleCallbackUnlocked(callsite, callback, internalCallback);
    }

    @Override
    public void setCallback(FutureCallback<T> callback) {
        setCallbackInternal(null, callback, null);
    }

    private void setComplete(Future<T> future, FutureCallsite callsite) {
        if (future instanceof SimpleFuture)
            ((SimpleFuture<T>)future).setCallbackInternal(callsite, null, this::setComplete);
        else
            future.setCallback(this::setComplete);
        setParent(future);
    }

    public void setComplete(Future<T> future) {
        if (future instanceof SimpleFuture)
            ((SimpleFuture<T>)future).setCallbackInternal(null, null, this::setComplete);
        else
            future.setCallback(this::setComplete);
        setParent(future);
    }

    /**
     * THIS METHOD IS FOR TEST USE ONLY
     * @return
     */
    @Deprecated
    public FutureCallback<T> getCallback() {
        return callback;
    }


    @Override
    public Future<T> success(SuccessCallback<T> callback) {
        final SimpleFuture<T> ret = new SimpleFuture<>();
        setCallbackInternal(null, null, (e, result, next) -> {
            if (e == null) {
                try {
                    callback.success(result);
                }
                catch (Exception callbackException) {
                    e = callbackException;
                    result = null;
                }
            }
            ret.setComplete(e, result, next);
        });
        return ret;
    }

    @Override
    public <R> Future<R> then(ThenFutureCallback<R, T> then) {
        final SimpleFuture<R> ret = new SimpleFuture<>();
        setCallbackInternal(null, null, (e, result, next) -> {
            if (e != null) {
                ret.setComplete(e, null, next);
                return;
            }
            Future<R> out;
            try {
                out = then.then(result);
            }
            catch (Exception callbackException) {
                ret.setComplete(callbackException, null, next);
                return;
            }
            ret.setComplete(out, next);

        });
        return ret;
    }

    @Override
    public <R> Future<R> thenConvert(final ThenCallback<R, T> callback) {
        return then(from -> new SimpleFuture<>(callback.then(from)));
    }

    @Override
    public Future<T> fail(FailFutureCallback<T> fail) {
        SimpleFuture<T> ret = new SimpleFuture<>();
        setCallbackInternal(null, null, (e, result, next) -> {
            if (e == null) {
                ret.setComplete(e, result, next);
                return;
            }
            Future<T> out;
            try {
                out = fail.fail(e);
            }
            catch (Exception callbackException) {
                ret.setComplete(callbackException, null, next);
                return;
            }
            ret.setComplete(out, next);
        });
        return ret;
    }

    @Override
    public Future<T> failConvert(FailCallback<T> fail) {
        return fail(SimpleFuture::new);
    }

    @Override
    public SimpleFuture<T> setParent(Cancellable parent) {
        super.setParent(parent);
        return this;
    }

    /**
     * Reset the future for reuse.
     * @return
     */
    public SimpleFuture<T> reset() {
        super.reset();

        result = null;
        exception = null;
        waiter = null;
        callback = null;
        silent = false;

        return this;
    }

    @Override
    public Exception tryGetException() {
        return exception;
    }

    @Override
    public T tryGet() {
        return result;
    }
}
