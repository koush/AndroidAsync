package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

public class FilteredDataEmitter implements DataEmitter, DataCallback {
    DataEmitter mEmitter;
    public DataEmitter getDataEmitter() {
        return mEmitter;
    }
    
    protected void report(Exception e) {
        if (getEndCallback() != null)
            getEndCallback().onCompleted(e);
    }

    public void setDataEmitter(DataEmitter emitter) {
        if (mEmitter != null) {
            mEmitter.setDataCallback(null);
        }
        mEmitter = emitter;
        mEmitter.setDataCallback(this);
        mEmitter.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                report(ex);
            }
        });
    }
    
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
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

    CompletedCallback mEndCallback;
    @Override
    public void setEndCallback(CompletedCallback callback) {
        mEndCallback = callback;
    }

    @Override
    public CompletedCallback getEndCallback() {
        return mEndCallback;
    }

    @Override
    public AsyncServer getServer() {
        return mEmitter.getServer();
    }
}
