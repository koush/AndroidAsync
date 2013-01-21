package com.koushikdutta.async;

import java.util.LinkedList;

import junit.framework.Assert;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ContinuationCallback;

public class Continuation {
    CompletedCallback callback;
    CompletedCallback wrapper;
    Runnable cancelCallback;
    public Continuation(CompletedCallback callback) {
        this(callback, null);
    }
    public Continuation(CompletedCallback callback, Runnable cancelCallback) {
        this.cancelCallback = cancelCallback;
        this.callback = callback;
        wrapper = new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
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
        completed = true;
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
        if (cancel) {
            reportCompleted(null);
            return;
        }
        while (mCallbacks.size() > 0 && !waiting) {
            ContinuationCallback cb = mCallbacks.remove();
            try {
                inNext = true;
                waiting = true;
                cb.onContinue(this, wrapper);
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
        
        reportCompleted(null);
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public boolean isCanceled() {
        return cancel;
    }
    
    public void cancel() {
        if (cancel)
            return;
        cancel = true;
        if (cancelCallback != null)
            cancelCallback.run();
    }
    
    public Runnable getCancelCallback() {
        return cancelCallback;
    }
    
    boolean cancel;
    boolean started;
    public Continuation start() {
        Assert.assertTrue(!started);
        started = true;
        next();
        return this;
    }
}
