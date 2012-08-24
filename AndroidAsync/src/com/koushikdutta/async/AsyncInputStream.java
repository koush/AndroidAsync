package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

public interface AsyncInputStream {
    public void read(int count, DataCallback callback);
}
