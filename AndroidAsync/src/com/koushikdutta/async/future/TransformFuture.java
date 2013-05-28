package com.koushikdutta.async.future;

public abstract class TransformFuture<T, F> extends SimpleFuture<T> implements FutureCallback<F> {
    @Override
    public void onCompleted(Exception e, F result) {
        if (e != null) {
            setComplete(e);
            return;
        }

        try {
           transform(result);
        }
        catch (Exception ex) {
            setComplete(ex);
        }
    }

    public TransformFuture<T, F> from(Future<F> future) {
        setParent(future);
        future.setCallback(this);
        return this;
    }

    protected abstract void transform(F result) throws Exception;
}