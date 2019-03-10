package com.koushikdutta.async.future;

public class SimpleCancellable implements DependentCancellable {
    boolean complete;
    @Override
    public boolean isDone() {
        return complete;
    }

    protected void cancelCleanup() {
    }

    protected void cleanup() {
    }

    protected void completeCleanup() {
    }

    public boolean setComplete() {
        synchronized (this) {
            if (cancelled)
                return false;
            if (complete) {
                // don't allow a Cancellable to complete twice...
                return false;
            }
            complete = true;
            parent = null;
        }
        completeCleanup();
        cleanup();
        return true;
    }

    @Override
    public boolean cancel() {
        Cancellable parent;
        synchronized (this) {
            if (complete)
                return false;
            if (cancelled)
                return true;
            cancelled = true;
            parent = this.parent;
            // null out the parent to allow garbage collection
            this.parent = null;
        }
        if (parent != null)
            parent.cancel();
        cancelCleanup();
        cleanup();
        return true;
    }
    boolean cancelled;

    private Cancellable parent;
    @Override
    public boolean setParent(Cancellable parent) {
        synchronized (this) {
            if (isDone())
                return false;
            this.parent = parent;
            return true;
        }
    }

    @Override
    public boolean isCancelled() {
        synchronized (this) {
            return cancelled || (parent != null && parent.isCancelled());
        }
    }

    public static final Cancellable COMPLETED = new SimpleCancellable() {
        {
            setComplete();
        }
    };

    public static final Cancellable CANCELLED = new SimpleCancellable() {
        {
            cancel();
        }
    };

    public Cancellable reset() {
        cancel();
        complete = false;
        cancelled = false;
        return this;
    }
}
