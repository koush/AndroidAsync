package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

public class BufferedDataEmitter implements DataEmitter, DataCallback {
    public BufferedDataEmitter() {
    }
    
    public void onDataAvailable() {
        if (mDataCallback != null && !mPaused && mBuffers.remaining() > 0)
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
        onDataAvailable();
    }

    @Override
    public boolean isPaused() {
        return mPaused;
    }
}
