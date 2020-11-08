package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;

public class BufferedDataSink implements DataSink {
    DataSink mDataSink;
    public BufferedDataSink(DataSink datasink) {
        setDataSink(datasink);
    }

    public boolean isBuffering() {
        return mPendingWrites.hasRemaining() || forceBuffering;
    }

    public boolean isWritable() {
        synchronized (mPendingWrites) {
            return mPendingWrites.remaining() < mMaxBuffer;
        }
    }

    public DataSink getDataSink() {
        return mDataSink;
    }

    boolean forceBuffering;
    public void forceBuffering(boolean forceBuffering) {
        this.forceBuffering = forceBuffering;
        if (!forceBuffering)
            writePending();
    }

    public void setDataSink(DataSink datasink) {
        mDataSink = datasink;
        mDataSink.setWriteableCallback(this::writePending);
    }

    private void writePending() {
        if (forceBuffering)
            return;

//        Log.i("NIO", "Writing to buffer...");
        boolean empty;
        synchronized (mPendingWrites) {
            mDataSink.write(mPendingWrites);
            empty = mPendingWrites.isEmpty();
        }
        if (empty) {
            if (endPending)
                mDataSink.end();
        }
        if (empty && mWritable != null)
            mWritable.onWriteable();
    }
    
    final ByteBufferList mPendingWrites = new ByteBufferList();

    // before the data is queued, let inheritors know. allows for filters, without
    // issues with having to filter before writing which may fail in the buffer.
    protected void onDataAccepted(ByteBufferList bb) {
    }

    @Override
    public void write(final ByteBufferList bb) {
        if (getServer().getAffinity() != Thread.currentThread()) {
            synchronized (mPendingWrites) {
                if (mPendingWrites.remaining() >= mMaxBuffer)
                    return;
                onDataAccepted(bb);
                bb.get(mPendingWrites);
            }
            getServer().post(this::writePending);
            return;
        }

        onDataAccepted(bb);

        if (!isBuffering())
            mDataSink.write(bb);

        synchronized (mPendingWrites) {
            bb.get(mPendingWrites);
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
        mMaxBuffer = maxBuffer;
    }

    @Override
    public boolean isOpen() {
        return mDataSink.isOpen();
    }

    boolean endPending;
    @Override
    public void end() {
        if (getServer().getAffinity() != Thread.currentThread()) {
            getServer().post(this::end);
            return;
        }

        synchronized (mPendingWrites) {
            if (mPendingWrites.hasRemaining()) {
                endPending = true;
                return;
            }
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
