package com.koushikdutta.async;

import junit.framework.Assert;

import com.koushikdutta.async.callback.DataCallback;

public class FilteredDataCallback implements DataEmitter, DataCallback, ExceptionEmitter {
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
        if (mExceptionCallback != null)
            mExceptionCallback.onException(e);
    }
    
    @Override
    public ExceptionCallback getExceptionCallback() {
        return mExceptionCallback;
    }
    
    @Override
    public void setExceptionCallback(ExceptionCallback callback) {
        mExceptionCallback = callback;
    }
    ExceptionCallback mExceptionCallback;
    
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        Assert.assertNotNull(mDataCallback);
        Util.emitAllData(this, bb);
    }
}
