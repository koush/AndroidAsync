package com.koushikdutta.async.future;

public interface Cancellable {
    boolean isDone();
    boolean isCancelled();
    boolean cancel();
}
