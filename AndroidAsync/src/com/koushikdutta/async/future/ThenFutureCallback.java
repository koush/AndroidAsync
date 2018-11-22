package com.koushikdutta.async.future;

public interface ThenFutureCallback<T, F> {
    /**
     * Callback that is invoked when Future.then completes,
     * and converts a value F to a Future<T>.
     * @param from
     * @return
     * @throws Exception
     */
    Future<T> then(F from) throws Exception;
}
