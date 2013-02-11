package com.koushikdutta.async;

import java.util.LinkedList;

import junit.framework.Assert;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ContinuationCallback;

public class Continuation implements ContinuationCallback, Runnable, Cancelable {
    CompletedCallback callback;
    Runnable cancelCallback;
    
    public CompletedCallback getCallback() {
        return callback;
    }
    public void setCallback(CompletedCallback callback) {
        this.callback = callback;
    }
    
    public Runnable getCancelCallback() {
        return cancelCallback;
    }
    public void setCancelCallback(Runnable cancelCallback) {
        this.cancelCallback = cancelCallback;
    }
    public void setCancelCallback(final Cancelable cancel) {
        if (cancel == null) {
            this.cancelCallback = null;
            return;
        }
        this.cancelCallback = new Runnable() {
            @Override
            public void run() {
                cancel.cancel();
            }
        };
    }
    
    public Continuation() {
        this(null);
    }
    public Continuation(CompletedCallback callback) {
        this(callback, null);
    }
    public Continuation(CompletedCallback callback, Runnable cancelCallback) {
        this.cancelCallback = cancelCallback;
        this.callback = callback;
    }
    
    private CompletedCallback wrap() {
        return new CompletedCallback() {
            boolean mThisCompleted;
            @Override
            public void onCompleted(Exception ex) {
                // onCompleted may be called more than once... buggy code.
                // only accept the first (timeouts, etc)
                if (mThisCompleted)
                    return;
                mThisCompleted = true;
                Assert.assertTrue(waiting);
                waiting = false;
                if (ex == null) {
                    next();
                    return;
                }
                reportCompleted(ex);
            }
        };
    }
    
    boolean completed;
    void reportCompleted(Exception ex) {
        if (cancel)
            return;
        completed = true;
        if (callback != null)
            callback.onCompleted(ex);        
    }
    
    LinkedList<ContinuationCallback> mCallbacks = new LinkedList<ContinuationCallback>();
    
    public void add(ContinuationCallback callback) {
        mCallbacks.add(callback);
    }
    
    public void insert(ContinuationCallback callback) {
        mCallbacks.add(0, callback);
    }
    
    private boolean inNext;
    private boolean waiting;
    private void next() {
        if (inNext)
            return;
        if (isCanceled()) {
            reportCompleted(null);
            return;
        }
        while (mCallbacks.size() > 0 && !waiting && !completed && !cancel) {
            ContinuationCallback cb = mCallbacks.remove();
            try {
                inNext = true;
                waiting = true;
                cb.onContinue(this, wrap());
            }
            catch (Exception e) {
                reportCompleted(e);
            }
            finally {
                inNext = false;
            }
        }
        if (waiting)
            return;
        if (completed)
            return;
        if (cancel)
            return;

        reportCompleted(null);
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public boolean isCanceled() {
        return cancel || (parent != null && parent.isCanceled());
    }
    
    public Cancelable cancel() {
        cancelThis();
        if (parent != null)
            parent.cancel();
        return this;
    }
    
    public void cancelThis() {
        if (isCanceled())
            return;
        cancel = true;
        if (cancelCallback != null)
            cancelCallback.run();
    }
    
    boolean cancel;
    boolean started;
    public Continuation start() {
        Assert.assertTrue(!started);
        started = true;
        next();
        return this;
    }

    Continuation parent;
    @Override
    public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
        parent = continuation;
        setCallback(next);
        setCancelCallback(continuation.getCancelCallback());
        start();
    }

    @Override
    public void run() {
        start();
    }
}
