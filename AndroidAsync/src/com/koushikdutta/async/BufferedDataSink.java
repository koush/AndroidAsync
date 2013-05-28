package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;

import java.nio.ByteBuffer;

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

    public boolean isBuffering() {
        return mPendingWrites != null;
    }
    
    public DataSink getDataSink() {
        return mDataSink;
    }

    private void writePending() {
//        Log.i("NIO", "Writing to buffer...");
        if (mPendingWrites != null) {
            mDataSink.write(mPendingWrites);
            if (mPendingWrites.remaining() == 0) {
                mPendingWrites = null;
                if (endPending)
                    mDataSink.end();
                if (closePending)
                    mDataSink.close();
            }
        }
        if (mPendingWrites == null && mWritable != null)
            mWritable.onWriteable();
    }
    
    ByteBufferList mPendingWrites;

    @Override
    public void write(ByteBuffer bb) {
        if (mPendingWrites == null)
            mDataSink.write(bb);

        if (bb.remaining() > 0) {
            int toRead = Math.min(bb.remaining(), mMaxBuffer);
            if (toRead > 0) {
                if (mPendingWrites == null)
                    mPendingWrites = new ByteBufferList();
                byte[] bytes = new byte[toRead];
                bb.get(bytes);
                mPendingWrites.add(ByteBuffer.wrap(bytes));
            }
        }
    }

    @Override
    public void write(ByteBufferList bb) {
        write(bb, false);
    }
    
    protected void write(ByteBufferList bb, boolean ignoreBuffer) {
        if (mPendingWrites == null)
            mDataSink.write(bb);

        if (bb.remaining() > 0) {
            int toRead = Math.min(bb.remaining(), mMaxBuffer);
            if (ignoreBuffer)
                toRead = bb.remaining();
            if (toRead > 0) {
                if (mPendingWrites == null)
                    mPendingWrites = new ByteBufferList();
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
        if (mPendingWrites == null)
            return 0;
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
        return !closePending && mDataSink.isOpen();
    }

    boolean closePending;
    @Override
    public void close() {
        if (mPendingWrites != null) {
            closePending = true;
            return;
        }
        mDataSink.close();
    }

    boolean endPending;
    @Override
    public void end() {
        if (mPendingWrites != null) {
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
