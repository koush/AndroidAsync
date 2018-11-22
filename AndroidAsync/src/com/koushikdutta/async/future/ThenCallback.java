package com.koushikdutta.async.future;

public interface ThenCallback<T, F> {
    Future<T> then(F from) throws Exception;
}
