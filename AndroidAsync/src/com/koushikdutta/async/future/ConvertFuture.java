package com.koushikdutta.async.future;

/**
 * Created by koush on 6/21/14.
 */
public abstract class ConvertFuture<T, F> extends TransformFuture<T, F> {
    @Override
    protected final void transform(F result) throws Exception {
        setComplete(convert(result));
    }

    protected abstract Future<T> convert(F result) throws Exception;
}
