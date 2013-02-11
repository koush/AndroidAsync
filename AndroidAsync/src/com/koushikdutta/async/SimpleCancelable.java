package com.koushikdutta.async;

import junit.framework.Assert;

public class SimpleCancelable implements Cancelable {
    boolean complete;
    @Override
    public boolean isCompleted() {
        return complete;
    }
    
    public SimpleCancelable setComplete(boolean complete) {
        Assert.assertTrue(this != COMPLETED);
        this.complete = complete;
        return this;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public Cancelable cancel() {
        canceled = true;
        return this;
    }
    boolean canceled;
    
    public static final SimpleCancelable COMPLETED = new SimpleCancelable().setComplete(true);
}
