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
            parent = null;
        }
        return true;
    }

    @Override
    public boolean cancel() {
        Cancellable parent;
        synchronized (this) {
            if (complete)
                return false;
            if (canceled)
                return true;
            canceled = true;
            parent = this.parent;
            // null out the parent to allow garbage collection
            this.parent = null;
        }
        if (parent != null)
            parent.cancel();
        return true;
    }
    boolean canceled;

    private Cancellable parent;
    @Override
    public SimpleCancelable setParent(Cancellable parent) {
        synchronized (this) {
            if (!isDone())
                this.parent = parent;
        }
        return this;
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
