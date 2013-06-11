package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.wrapper.DataEmitterWrapper;

public class FilteredDataEmitter extends DataEmitterBase implements DataEmitter, DataCallback, DataEmitterWrapper, DataTrackingEmitter {
    DataEmitter mEmitter;
    @Override
    public DataEmitter getDataEmitter() {
        return mEmitter;
    }

    @Override
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
    public int getBytesRead() {
        return totalRead;
    }

    @Override
    public DataTracker getDataTracker() {
        return tracker;
    }

    @Override
    public void setDataTracker(DataTracker tracker) {
        this.tracker = tracker;
    }

    DataTracker tracker;
    int totalRead;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (bb != null)
            totalRead += bb.remaining();
        Util.emitAllData(this, bb);
        if (bb != null)
            totalRead -= bb.remaining();
        if (tracker != null && bb != null)
            tracker.onData(totalRead);
        // if there's data after the emitting, and it is paused... the underlying implementation
        // is obligated to cache the byte buffer list.
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
    public AsyncServer getServer() {
        return mEmitter.getServer();
    }

    @Override
    public void close() {
        mEmitter.close();
    }
}
