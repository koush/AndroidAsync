package com.koushikdutta.async.future;

public interface FailRecoverCallback<T> {
    /**
     * Callback that is invoked when a future completes with an error.
     * The error should be rethrown, or a new future value should be returned.
     * @param e
     * @return
     * @throws Exception
     */
    Future<T> fail(Exception e) throws Exception;
}
