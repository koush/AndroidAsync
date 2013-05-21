package com.koushikdutta.async.future;

/**
 * Created by koush on 5/20/13.
 */
public interface FutureCallback<T> {
    public void onCompleted(Exception e, T result);
}
