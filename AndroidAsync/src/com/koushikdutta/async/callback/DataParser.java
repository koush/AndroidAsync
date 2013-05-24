package com.koushikdutta.async.callback;

/**
 * Created by koush on 5/22/13.
 */
public interface DataParser<T> extends DataCallback {
    public T get();
}
