package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.wrapper.DataEmitterWrapper;

public class FilteredDataEmitter extends DataEmitterBase implements DataEmitter, DataCallback, DataEmitterWrapper, DataTrackingEmitter {
    private DataEmitter mEmitter;
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

    private DataTracker tracker;
    private int totalRead;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (closed) {
            // this emitter was closed but for some reason data is still being spewed...
            // eat it, nom nom.
            bb.recycle();
            return;
        }
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

    boolean closed;
    @Override
    public void close() {
        closed = true;
        if (mEmitter != null)
            mEmitter.close();
    }

    @Override
    public String charset() {
        if (mEmitter == null)
            return null;
        return mEmitter.charset();
    }
}
