package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

public class BufferedDataEmitter implements DataEmitter {
    DataEmitter mEmitter;
    public BufferedDataEmitter(DataEmitter emitter) {
        mEmitter = emitter;
        mEmitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                bb.get(mBuffers);
                BufferedDataEmitter.this.onDataAvailable();
            }
        });

        mEmitter.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                mEnded = true;
                mEndException = ex;
                if (mBuffers.remaining() == 0 && mEndCallback != null)
                    mEndCallback.onCompleted(ex);
            }
        });
    }

    @Override
    public void close() {
        mEmitter.close();
    }

    boolean mEnded = false;
    Exception mEndException;
    
    public void onDataAvailable() {
        if (mDataCallback != null && !isPaused() && mBuffers.remaining() > 0)
            mDataCallback.onDataAvailable(this, mBuffers);

        if (mEnded && !mBuffers.hasRemaining() && mEndCallback != null)
            mEndCallback.onCompleted(mEndException);
    }
    
    ByteBufferList mBuffers = new ByteBufferList();

    DataCallback mDataCallback;
    @Override
    public void setDataCallback(DataCallback callback) {
        if (mDataCallback != null)
            throw new RuntimeException("Buffered Data Emitter callback may only be set once");
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
    public void pause() {
        mEmitter.pause();
    }

    @Override
    public void resume() {
        mEmitter.resume();
        onDataAvailable();
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

    @Override
    public String charset() {
        return mEmitter.charset();
    }
}
