package com.koushikdutta.async.future;

public abstract class MultiTransformFuture<T, F> extends MultiFuture<T> implements FutureCallback<F> {
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

    protected void error(Exception e) {
        setComplete(e);
    }

    protected abstract void transform(F result) throws Exception;
}