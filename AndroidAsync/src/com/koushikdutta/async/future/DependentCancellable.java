package com.koushikdutta.async.future;

public interface DependentCancellable extends Cancellable {
    public Cancellable getParent();
    public DependentCancellable setParent(Cancellable parent);
}
