package com.koushikdutta.async.future;

public interface FailCallback {
    /**
     * Callback that is invoked when a future completes with an error.
     * The error should be rethrown, or a new value should be returned.
     * @param e
     * @throws Exception
     */
    void fail(Exception e) throws Exception;
}
