package com.koushikdutta.async.future;

/**
 * Created by koush on 5/20/13.
 */
public interface FutureCallback<T> {
    /**
     * onCompleted is called by the Future with the result or exception of the asynchronous operation.
     * @param e Exception encountered by the operation
     * @param result Result returned from the operation
     */
    public void onCompleted(Exception e, T result);
}
