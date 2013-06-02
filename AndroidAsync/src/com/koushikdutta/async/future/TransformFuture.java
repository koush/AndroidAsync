package com.koushikdutta.async.future;

public abstract class TransformFuture<T, F> extends SimpleFuture<T> implements FutureCallback<F> {
    @Override
    public void onCompleted(Exception e, F result) {
        if (isCancelled())
            return;
        if (e != null) {
            error(e);
            return;
        }

        try {
           transform(result);
        }
        catch (Exception ex) {
            error(ex);
        }
    }

    public TransformFuture<T, F> from(Future<F> future) {
        setParent(future);
        future.setCallback(this);
        return this;
    }

    protected void error(Exception e) {
        setComplete(e);
    }
    protected abstract void transform(F result) throws Exception;
}