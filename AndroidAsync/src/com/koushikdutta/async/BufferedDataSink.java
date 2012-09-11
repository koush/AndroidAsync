package com.koushikdutta.async;

import java.nio.ByteBuffer;

import junit.framework.Assert;
import android.util.Log;

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
        mDataSink.write(mPendingWrites);
        if (mPendingWrites.remaining() == 0) {
            mPendingWrites = null;
            onFlushed();
        }
    }
    
    ByteBufferList mPendingWrites;

    @Override
    public void write(ByteBuffer bb) {
        if (mPendingWrites == null) {
            mDataSink.write(bb);
            if (bb.remaining() > 0) {
                mPendingWrites = new ByteBufferList();
                mPendingWrites.add(ByteBuffer.wrap(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining()));
                bb.position(0);
                bb.limit(0);
            }
        }
        else {
            mPendingWrites.add(ByteBuffer.wrap(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining()));
            bb.position(0);
            bb.limit(0);
            writePending();
        }
    }

    @Override
    public void write(ByteBufferList bb) {
        if (mPendingWrites == null) {
            mDataSink.write(bb);
            if (bb.remaining() > 0) {
                mPendingWrites = new ByteBufferList();
                mPendingWrites.add(bb);
            }
            bb.clear();
        }
        else {
            mPendingWrites.add(bb);
            bb.clear();
            writePending();
        }
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        Assert.fail("BufferingDataSink is always writeable.");
    }

    @Override
    public WritableCallback getWriteableCallback() {
        Assert.fail("BufferingDataSink is always writeable.");
        return null;
    }
    
    public int remaining() {
        if (mPendingWrites == null)
            return 0;
        return mPendingWrites.remaining();
    }

    public void onFlushed() {
    }
}
