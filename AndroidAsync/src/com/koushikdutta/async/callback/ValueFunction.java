package com.koushikdutta.async.callback;

public interface ValueFunction<T> {
    T getValue() throws Exception;
}
