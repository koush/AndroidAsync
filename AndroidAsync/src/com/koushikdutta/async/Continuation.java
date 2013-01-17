package com.koushikdutta.async;

import java.util.LinkedList;

import junit.framework.Assert;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ContinuationCallback;

public class Continuation {
    CompletedCallback callback;
    CompletedCallback wrapper;
    public Continuation(CompletedCallback callback) {
        this.callback = callback;
        wrapper = new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex == null) {
                    waiting = false;
                    next();
                    return;
                }
                Continuation.this.callback.onCompleted(ex);
            }
        };
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
        while (mCallbacks.size() > 0 && !waiting) {
            ContinuationCallback cb = mCallbacks.remove();
            try {
                inNext = true;
                waiting = true;
                cb.onContinue(wrapper);
            }
            catch (Exception e) {
                callback.onCompleted(e);
            }
            finally {
                inNext = false;
            }
        }
        if (waiting)
            return;
        
        callback.onCompleted(null);
    }
    
    boolean started;
    public void start() {
        Assert.assertTrue(!started);
        started = true;
        next();
    }
}
