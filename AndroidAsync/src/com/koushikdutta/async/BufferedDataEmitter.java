package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

public class BufferedDataEmitter implements DataEmitter, DataCallback {
    public BufferedDataEmitter() {
    }
    
    public void onDataAvailable() {
        if (mDataCallback != null)
            mDataCallback.onDataAvailable(this, mBuffers);
    }
    
    ByteBufferList mBuffers = new ByteBufferList();

    DataCallback mDataCallback;
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
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        mBuffers.add(bb);
        bb.clear();

        onDataAvailable();        
    }

}
