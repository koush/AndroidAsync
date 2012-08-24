package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import junit.framework.Assert;

import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;

class AsyncSocketImpl implements AsyncSocket {
    AsyncSocketImpl() {
    }
    
    public boolean isChunked() {
        return mChannel.isChunked();
    }
    
    void attach(SocketChannel channel) throws IOException {
        mChannel = new SocketChannelWrapper(channel);
    }
    
    void attach(DatagramChannel channel) throws IOException {
        mChannel = new DatagramChannelWrapper(channel);
    }
    
    ChannelWrapper getChannel() {
        return mChannel;
    }
    
    public void onDataWritable() {
        Assert.assertNotNull(mWriteableHandler);
        mWriteableHandler.onWriteable();
    }
    
    private ChannelWrapper mChannel;
    SelectionKey mKey;
    
    @Override
    public void write(ByteBufferList list) {
        if (!mChannel.isConnected()) {
            Assert.assertFalse(mChannel.isChunked());
            return;
        }

        try {
            mChannel.write(list.toArray());
            handleRemaining(list.remaining());
        }
        catch (IOException e) {
        }
    }
    
    private void handleRemaining(int remaining) {
        if (remaining > 0) {
            // chunked channels should not fail
            Assert.assertFalse(mChannel.isChunked());
            // register for a write notification if a write fails
            mKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
        else {
            mKey.interestOps(SelectionKey.OP_READ);
        }
    }

    @Override
    public void write(ByteBuffer b) {
        try {
            if (!mChannel.isConnected()) {
                Assert.assertFalse(mChannel.isChunked());
                return;
            }

            // keep writing until the the socket can't write any more, or the
            // data is exhausted.
            mChannel.write(b);
            handleRemaining(b.remaining());
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    int onReadable() {
        int total = 0;
        try {
            boolean closed = false;
            ByteBufferList list = new ByteBufferList();
            while (true) {
                ByteBuffer b = ByteBuffer.allocate(2 << 10);
                list.add(b);
                int read = mChannel.read(b);
                b.limit(b.position());
                b.position(0);
                if (read < 0) {
                    close();
                    closed = true;
                }
                else {
                    total += read;
                }
                if (read <= 0)
                    break;
                if (mChannel.isChunked()) {
                    Assert.assertNotNull(mDataHandler);
                    mDataHandler.onDataAvailable(this, list);
                    list = new ByteBufferList();
                }
            }
            if (!mChannel.isChunked()) {
//                Util.emitAllData(this, list);
                int remaining;
                while (mDataHandler != null && (remaining = list.remaining()) > 0) {
                    DataCallback handler = mDataHandler;
                    mDataHandler.onDataAvailable(this, list);
                    if (remaining == list.remaining() && handler == mDataHandler) {
                        Assert.fail("mDataHandler failed to consume data, yet remains the mDataHandler.");
                        break;
                    }
                }
                Assert.assertEquals(list.remaining(), 0);
            }
        
            if (closed)
                reportClose();
        }
        catch (Exception e) {
            close();
            report(e);
            reportClose();
        }
        
        return total;
    }
    
    private void reportClose() {
        if (mClosedHander != null)
            mClosedHander.onClosed();
    }

    @Override
    public void close() {
        mKey.cancel();
        try {
            mChannel.close();
        }
        catch (IOException e) {
        }
    }

    WritableCallback mWriteableHandler;
    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mWriteableHandler = handler;        
    }

    DataCallback mDataHandler;
    @Override
    public void setDataCallback(DataCallback callback) {
        mDataHandler = callback;
    }

    @Override
    public DataCallback getDataCallback() {
        return mDataHandler;
    }

    ClosedCallback mClosedHander;
    @Override
    public void setClosedCallback(ClosedCallback handler) {
        mClosedHander = handler;       
    }

    @Override
    public ClosedCallback getCloseHandler() {
        return mClosedHander;
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mWriteableHandler;
    }

    void report(Exception e) {
        if (mExceptionCallback != null)
            mExceptionCallback.onException(e);
        else
            e.printStackTrace();
    }
    
    private ExceptionCallback mExceptionCallback;
    @Override
    public void setExceptionCallback(ExceptionCallback callback) {
        mExceptionCallback = callback;
    }

    @Override
    public ExceptionCallback getExceptionCallback() {
        return mExceptionCallback;
    }
}
