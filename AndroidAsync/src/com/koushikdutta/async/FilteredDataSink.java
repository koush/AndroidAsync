package com.koushikdutta.async;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.callback.WritableCallback;

public class FilteredDataSink implements DataSink {
    public FilteredDataSink(DataSink sink) {
        mSink = sink;
        mSink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                write((ByteBufferList)null);
                if (mPendingWritable) {
                    mPendingWritable = false;
                    mWritable.onWriteable();
                }
                testFlushed();
            }
        });
    }
    
    private void testFlushed() {
        if (mPending == null)
            onFlushed();
    }

    protected void onFlushed() {
    }
    
    public int getPending() {
        if (mPending == null)
            return 0;
        return mPending.remaining();
    }
    
    DataSink mSink;
    public DataSink getSink() {
        return mSink;
    }
    
    ByteBufferList mPending;
    boolean mPendingWritable = false;
    public ByteBufferList filter(ByteBufferList bb) {
        return bb;
    }

    @Override
    public final void write(ByteBuffer bb) {
        if (!handlePending(bb))
            return;
        ByteBufferList list = new ByteBufferList();
        list.add(bb);
        write(list);
        bb.position(0);
        bb.limit(0);
    }
    private boolean handlePending(Object bb) {
        if (mPending != null) {
            // only attempt to write the pending if invoked with null,
            // which means it is in response to onWritable of the underlying
            // sink.
            if (bb != null) {
                mPendingWritable = true;
                return false;
            }
            mSink.write(mPending);

            if (mPending.remaining() == 0) {
                mPending = null;
            }
            else {
                return false;
            }
        }
        if (bb == null)
            return false;
        return true;
    }

    @Override
    public final void write(ByteBufferList bb) {
        if (!handlePending(bb))
            return;
        ByteBufferList filtered = filter(bb);
        Assert.assertTrue(bb == null || filtered == bb || bb.remaining() == 0);
        mSink.write(filtered);
        if (filtered.remaining() > 0) {
            mPending = new ByteBufferList();
            mPending.add(filtered.read(filtered.remaining()));
        }
        bb.clear();
        testFlushed();
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
}
