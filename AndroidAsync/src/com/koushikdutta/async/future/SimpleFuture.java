package com.koushikdutta.async.future;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.koushikdutta.async.AsyncServer.AsyncSemaphore;
import com.koushikdutta.async.callback.ResultCallback;

public class SimpleFuture<T> extends SimpleCancelable implements DependentFuture<T> {
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    protected void cancelCleanup() {
    }
    
    @Override
    public boolean cancel() {
        if (super.cancel()) {
            synchronized (this) {
                exception = new CancellationException();
                cancelCleanup();
                if (waiter != null)
                    waiter.release();
            }
            return true;
        }

        return false;
    }

    AsyncSemaphore waiter;
    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResult();
            if (waiter == null)
                waiter = new AsyncSemaphore();
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
        synchronized (this) {
            if (isCancelled() || isDone())
                return getResult();
            if (waiter == null)
                waiter = new AsyncSemaphore();
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

    Exception exception;
    public boolean setComplete(Exception e) {
        FutureCallback<T> callback;
        synchronized (this) {
            if (!super.setComplete())
                return false;
            if (waiter != null)
                waiter.release();
            exception = e;
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
            if (waiter != null)
                waiter.release();
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
    public SimpleFuture<T> setParent(Cancellable parent) {
        super.setParent(parent);
        return this;
    }
}
