package com.koushikdutta.async;

import junit.framework.Assert;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

public class FilteredDataCallback implements DataEmitter, DataCallback, CompletedEmitter {
    public FilteredDataCallback() {
    }

    private DataCallback mDataCallback;
    @Override
    public void setDataCallback(DataCallback callback) {
        mDataCallback = callback;
    }
    
    @Override
    public DataCallback getDataCallback() {
        return mDataCallback;
    }
    
    @Override
    public boolean isChunked() {
        return false;
    }
    
    protected void report(Exception e) {
        if (mCompletedCallback != null)
            mCompletedCallback.onCompleted(e);
    }
    
    @Override
    public CompletedCallback getCompletedCallback() {
        return mCompletedCallback;
    }
    
    @Override
    public void setCompletedCallback(CompletedCallback callback) {
        mCompletedCallback = callback;
    }
    CompletedCallback mCompletedCallback;
    
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        Assert.assertNull(pending);
        Assert.assertNotNull(mDataCallback);
        Util.emitAllData(this, bb);
        if (bb.remaining() > 0)
            pending = bb;
    }

    private ByteBufferList pending;
    private boolean mPaused;
    @Override
    public void pause() {
        mPaused = true;
    }

    @Override
    public void resume() {
        if (!mPaused)
            return;
        mPaused = false;
        if (pending != null) {
            Assert.assertNotNull(mDataCallback);
            Util.emitAllData(this, pending);
            if (pending.remaining() == 0)
                pending = null;
        }
    }

    @Override
    public boolean isPaused() {
        return mPaused;
    }
}
