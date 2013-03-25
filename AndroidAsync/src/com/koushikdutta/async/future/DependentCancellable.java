package com.koushikdutta.async.future;

public interface DependentCancellable extends Cancellable {
    public Cancellable getParent();
    public void setParent(Cancellable parent);
}
