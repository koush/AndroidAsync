package com.koushikdutta.async.future;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.koushikdutta.async.AsyncServer.AsyncSemaphore;

public class SimpleFuture<T> extends SimpleCancelable implements DependentFuture<T> {
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }
    
    @Override
    public boolean cancel() {
        if (super.cancel()) {
            synchronized (this) {
                exception = new CancellationException();
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
            if (isCancelled())
                return null;
            if (isDone())
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
            if (isCancelled())
                throw new ExecutionException(new CancellationException());
            if (isDone())
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


    Exception exception;
    public boolean setComplete(Exception e) {
        synchronized (this) {
            if (!super.setComplete())
                return false;
            if (waiter != null)
                waiter.release();
            exception = e;
            return true;
        }
    }

    T result;
    public boolean setComplete(T value) {
        synchronized (this) {
            if (!super.setComplete())
                return false;
            result = value;
            if (waiter != null)
                waiter.release();
            return true;
        }
    }
}
