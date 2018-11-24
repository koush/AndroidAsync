package com.koushikdutta.async.future;

public interface FailConvertCallback<T> {
    /**
     * Callback that is invoked when a future completes with an error.
     * The error should be rethrown, or a new value should be returned.
     * @param e
     * @return
     * @throws Exception
     */
    T fail(Exception e) throws Exception;
}
