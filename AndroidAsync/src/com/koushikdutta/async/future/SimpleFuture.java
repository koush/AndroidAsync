package com.koushikdutta.async.future;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.koushikdutta.async.AsyncServer.AsyncSemaphore;

public class SimpleFuture<T> extends SimpleCancellable implements DependentFuture<T> {
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    @Override
    public boolean cancel() {
        if (!super.cancel())
            return false;
        // still need to release any pending waiters
        synchronized (this) {
            exception = new CancellationException();
            releaseWaiterLocked();
        }
        return true;
    }

    AsyncSemaphore waiter;
    @Override
    public T get() throws InterruptedException, ExecutionException {
        AsyncSemaphore waiter;
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResult();
            waiter = ensureWaiterLocked();
        }
        waiter.acquire();
        return getResult();
    }
    
    private T getResult() throws ExecutionException {
        if (exception != null)
            throw new ExecutionException(exception);
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        AsyncSemaphore waiter;
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResult();
            waiter = ensureWaiterLocked();
        }
        if (!waiter.tryAcquire(timeout, unit))
            throw new TimeoutException();
        return getResult();
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

    private void handleCallbackUnlocked(FutureCallback<T> callback) {
        if (callback != null)
            callback.onCompleted(exception, result);
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

    Exception exception;
    public boolean setComplete(Exception e) {
        FutureCallback<T> callback;
        synchronized (this) {
            if (!super.setComplete())
                return false;
            exception = e;
            releaseWaiterLocked();
            callback = handleCompleteLocked();
        }
        handleCallbackUnlocked(callback);
        return true;
    }

    T result;
    public boolean setComplete(T value) {
        FutureCallback<T> callback;
        synchronized (this) {
            if (!super.setComplete())
                return false;
            result = value;
            releaseWaiterLocked();
            callback = handleCompleteLocked();
        }
        handleCallbackUnlocked(callback);
        return true;
    }

    public boolean setComplete(Exception e, T value) {
        if (e != null)
            return setComplete(e);
        return setComplete(value);
    }

    public FutureCallback<T> getCompletionCallback() {
        return new FutureCallback<T>() {
            @Override
            public void onCompleted(Exception e, T result) {
                setComplete(e, result);
            }
        };
    }

    FutureCallback<T> callback;

    @Override
    public SimpleFuture<T> setCallback(FutureCallback<T> callback) {
        // callback can only be changed or read/used inside a sync block
        synchronized (this) {
            this.callback = callback;
            if (isDone())
                callback = handleCompleteLocked();
            else
                callback = null;
        }
        handleCallbackUnlocked(callback);
        return this;
    }

    @Override
    public <C extends FutureCallback<T>> C then(C callback) {
        if (callback instanceof DependentCancellable)
            ((DependentCancellable)callback).setParent(this);
        setCallback(callback);
        return callback;
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

        return this;
    }
}
