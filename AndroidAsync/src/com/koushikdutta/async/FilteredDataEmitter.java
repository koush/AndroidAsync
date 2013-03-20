package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

public class FilteredDataEmitter implements DataEmitter {
    DataEmitter mEmitter;
    public DataEmitter getDataEmitter() {
        return mEmitter;
    }
    
    public void setDataEmitter(DataEmitter emitter) {
        if (mEmitter != null) {
            mEmitter.setDataCallback(null);
        }
        mEmitter = emitter;
        mEmitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                FilteredDataEmitter.this.onDataAvailable(emitter, bb);
            }
        });
    }
    
    protected void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        Util.emitAllData(this, bb);
        // if there's data after the emitting, and it is paused... the underlying implementation
        // is obligated to cache the byte buffer list.
    }

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
        return mEmitter.isChunked();
    }

    @Override
    public void pause() {
        mEmitter.pause();
    }

    @Override
    public void resume() {
        mEmitter.resume();
    }

    @Override
    public boolean isPaused() {
        return mEmitter.isPaused();
    }

    @Override
    public void setEndCallback(CompletedCallback callback) {
        mEmitter.setEndCallback(callback);
    }

    @Override
    public CompletedCallback getEndCallback() {
        return mEmitter.getEndCallback();
    }
}
