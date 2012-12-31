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
    
    private void next() {
        if (mCallbacks.size() > 0) {
            ContinuationCallback cb = mCallbacks.remove();
            try {
                cb.onContinue(wrapper);
            }
            catch (Exception e) {
                callback.onCompleted(e);
            }
            return;
        }
        
        callback.onCompleted(null);
    }
    
    boolean started;
    public void start() {
        Assert.assertTrue(!started);
        started = true;
        next();
    }
}
