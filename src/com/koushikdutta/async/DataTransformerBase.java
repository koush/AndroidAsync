package com.koushikdutta.async;

import junit.framework.Assert;

import com.koushikdutta.async.callback.DataCallback;

public class DataTransformerBase implements DataTransformer {
    public DataTransformerBase() {
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
    
    @Override
    public void onException(Exception error) {
        error.printStackTrace();
    }
    
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        Assert.assertNotNull(mDataCallback);
        Util.emitAllData(this, bb);
    }
}
