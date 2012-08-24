package com.koushikdutta.async;

import junit.framework.Assert;

import com.koushikdutta.async.callback.DataCallback;

public class DataEmitterStream implements AsyncInputStream {
    DataCallback mPendingRead;
    int mPendingReadLength;
    ByteBufferList mPendingData = new ByteBufferList();
    @Override
    public void read(int count, DataCallback callback) {
        Assert.assertNull(mPendingRead);    
        mPendingReadLength = count;
        mPendingRead = callback;
        mPendingData = new ByteBufferList();
        mEmitter.setDataCallback(mMyHandler);
    }
    
    DataCallback mMyHandler = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            // if we're registered for data, we must be waiting for a read
            Assert.assertNotNull(mPendingRead);
            do {
                int need = Math.min(bb.remaining(), mPendingReadLength - mPendingData.remaining());
                mPendingData.add(bb.get(need));
            }
            while (handlePendingData() && mPendingRead != null);
        }
    };

    private boolean handlePendingData() {
        if (mPendingReadLength > mPendingData.remaining())
            return false;

        DataCallback pendingRead = mPendingRead;
        mPendingRead = null;
        pendingRead.onDataAvailable(mEmitter, mPendingData);

        return true;
    }
    
    DataEmitter mEmitter;
    public DataEmitterStream(DataEmitter emitter) {
        Assert.assertFalse(emitter.isChunked());
        mEmitter = emitter;
    }
}
