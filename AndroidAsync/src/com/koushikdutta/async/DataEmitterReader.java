package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

public class DataEmitterReader implements com.koushikdutta.async.callback.DataCallback {
    DataCallback mPendingRead;
    int mPendingReadLength;
    ByteBufferList mPendingData = new ByteBufferList();

    public void read(int count, DataCallback callback) {
        assert mPendingRead == null;
        mPendingReadLength = count;
        mPendingRead = callback;
        assert !mPendingData.hasRemaining();
        mPendingData.recycle();
    }

    private boolean handlePendingData(DataEmitter emitter) {
        if (mPendingReadLength > mPendingData.remaining())
            return false;

        DataCallback pendingRead = mPendingRead;
        mPendingRead = null;
        pendingRead.onDataAvailable(emitter, mPendingData);
        assert !mPendingData.hasRemaining();

        return true;
    }

    public DataEmitterReader() {
    }
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        // if we're registered for data, we must be waiting for a read
        assert mPendingRead != null;
        do {
            int need = Math.min(bb.remaining(), mPendingReadLength - mPendingData.remaining());
            bb.get(mPendingData, need);
            bb.remaining();
        }
        while (handlePendingData(emitter) && mPendingRead != null);
        bb.remaining();
    }
}
