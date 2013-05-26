package com.koushikdutta.async.future;

public interface DependentCancellable extends Cancellable {
    public DependentCancellable setParent(Cancellable parent);
}
