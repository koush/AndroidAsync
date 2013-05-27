package com.koushikdutta.async.callback;

import com.koushikdutta.async.callback.DataCallback;

/**
 * Created by koush on 5/22/13.
 */
public interface DataParser<T> extends DataCallback {
    public T get();
}
