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
        mDataSink.write(mPendingWrites);
    }
    
    ByteBufferList mPendingWrites = new ByteBufferList();

    @Override
    public void write(ByteBuffer bb) {
        mPendingWrites.add(ByteBuffer.wrap(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining()));
        bb.position(0);
        bb.limit(0);
        writePending();
    }

    @Override
    public void write(ByteBufferList bb) {
        mPendingWrites.add(bb);
        bb.clear();
        writePending();
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
}
