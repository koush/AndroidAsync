package com.koushikdutta.async.future;

public class SimpleCancelable implements DependentCancellable {
    boolean complete;
    @Override
    public boolean isDone() {
        return complete;
    }
    
    public boolean setComplete() {
        synchronized (this) {
            if (canceled)
                return false;
            complete = true;
        }
        return true;
    }

    @Override
    public boolean cancel() {
        synchronized (this) {
            if (complete)
                return false;
            if (canceled)
                return true;
            canceled = true;
        }
        if (parent != null)
            parent.cancel();
        return true;
    }
    boolean canceled;

    Cancellable parent;
    @Override
    public Cancellable getParent() {
        return parent;
    }
    
    @Override
    public void setParent(Cancellable parent) {
        this.parent = parent;
    }

    @Override
    public boolean isCancelled() {
        return canceled || (parent != null && parent.isCancelled());
    }

    public static final Cancellable COMPLETED = new SimpleCancelable() {
        {
            setComplete();
        }
    };
}
