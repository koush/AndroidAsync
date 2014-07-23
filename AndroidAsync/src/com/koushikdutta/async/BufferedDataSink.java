package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;

import java.nio.ByteBuffer;

public class BufferedDataSink implements DataSink {
    DataSink mDataSink;
    public BufferedDataSink(DataSink datasink) {
        setDataSink(datasink);
    }

    public boolean isBuffering() {
        return mPendingWrites.hasRemaining();
    }
    
    public DataSink getDataSink() {
        return mDataSink;
    }

    public void setDataSink(DataSink datasink) {
        mDataSink = datasink;
        mDataSink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                writePending();
            }
        });
    }

    private void writePending() {
//        Log.i("NIO", "Writing to buffer...");
        if (mPendingWrites.hasRemaining()) {
            mDataSink.write(mPendingWrites);
            if (mPendingWrites.remaining() == 0) {
                if (endPending)
                    mDataSink.end();
            }
        }
        if (!mPendingWrites.hasRemaining() && mWritable != null)
            mWritable.onWriteable();
    }
    
    ByteBufferList mPendingWrites = new ByteBufferList();

    @Override
    public void write(ByteBufferList bb) {
        write(bb, false);
    }
    
    protected void write(ByteBufferList bb, boolean ignoreBuffer) {
        if (!mPendingWrites.hasRemaining())
            mDataSink.write(bb);

        if (bb.remaining() > 0) {
            int toRead = Math.min(bb.remaining(), mMaxBuffer);
            if (ignoreBuffer)
                toRead = bb.remaining();
            if (toRead > 0) {
                bb.get(mPendingWrites, toRead);
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
        return mPendingWrites.remaining();
    }
    
    int mMaxBuffer = Integer.MAX_VALUE;
    public int getMaxBuffer() {
        return mMaxBuffer;
    }
    
    public void setMaxBuffer(int maxBuffer) {
        assert maxBuffer >= 0;
        mMaxBuffer = maxBuffer;
    }

    @Override
    public boolean isOpen() {
        return mDataSink.isOpen();
    }

    boolean endPending;
    @Override
    public void end() {
        if (mPendingWrites.hasRemaining()) {
            endPending = true;
            return;
        }
        mDataSink.end();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mDataSink.setClosedCallback(handler);
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mDataSink.getClosedCallback();
    }

    @Override
    public AsyncServer getServer() {
        return mDataSink.getServer();
    }
}
