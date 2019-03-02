package com.koushikdutta.async.future;

public interface DoneCallback<T> {
    void done(Exception e, T result) throws Exception;
}
