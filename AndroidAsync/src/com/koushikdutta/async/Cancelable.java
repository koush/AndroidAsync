package com.koushikdutta.async;

public interface Cancelable {
    boolean isCompleted();
    boolean isCanceled();
    Cancelable cancel();
}
