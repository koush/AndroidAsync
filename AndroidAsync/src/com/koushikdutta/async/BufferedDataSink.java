package com.koushikdutta.async;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.callback.WritableCallback;

public class BufferedDataSink implements DataSink {
    DataSink mDataSink;
    public BufferedDataSink(DataSink datasink) {
        mDataSink = datasink;
        mDataSink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                writePending();
            }
        });
    }
    
    public DataSink getDataSink() {
        return mDataSink;
    }

    private void writePending() {
//        Log.i("NIO", "Writing to buffer...");
        if (mPendingWrites != null) {
            mDataSink.write(mPendingWrites);
            if (mPendingWrites.remaining() == 0)
                mPendingWrites = null;
        }
        if (mPendingWrites == null && mWritable != null)
            mWritable.onWriteable();
    }
    
    ByteBufferList mPendingWrites;

    @Override
    public void write(ByteBuffer bb) {
        ByteBufferList bbl = new ByteBufferList();
        bbl.add(bb);
        write(bbl);
    }

    @Override
    public void write(ByteBufferList bb) {
        if (mPendingWrites == null)
            mDataSink.write(bb);
//        else
//            Assert.assertTrue(mPendingWrites.remaining() <= mMaxBuffer);

        if (bb.remaining() > 0) {
            int toRead = Math.min(bb.remaining(), mMaxBuffer);
            if (toRead > 0) {
                if (mPendingWrites == null)
                    mPendingWrites = new ByteBufferList();
                mPendingWrites.add(bb.get(toRead));
            }
        }
    }

    WritableCallback mWritable;
    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mWritable = handler;
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mWritable;
    }
    
    public int remaining() {
        if (mPendingWrites == null)
            return 0;
        return mPendingWrites.remaining();
    }
    
    int mMaxBuffer = Integer.MAX_VALUE;
    public int getMaxBuffer() {
        return mMaxBuffer;
    }
    
    public void setMaxBuffer(int maxBuffer) {
        Assert.assertTrue(maxBuffer >= 0);
        mMaxBuffer = maxBuffer;
    }
}
