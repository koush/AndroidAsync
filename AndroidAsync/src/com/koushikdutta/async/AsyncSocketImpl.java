package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import junit.framework.Assert;

import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
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
            close();
            report(e);
            reportClose();
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
            close();
            report(ex);
            reportClose();
        }
    }

    private ByteBuffer pending;
    private void spit(ByteBuffer b) {
        ByteBufferList list = new ByteBufferList();
        list.add(b);
        Util.emitAllData(this, list);
        if (list.remaining() == 0) {
            b.position(0);
            b.limit(0);
        }
    }
    
    int mToAlloc = 0;
    int onReadable() {
        spitPending();
        // even if the socket is paused,
        // it may end up getting a queued readable event if it is
        // already in the selector's ready queue.
        if (mPaused)
            return 0;
        int total = 0;
        try {
            boolean closed = false;
            int maxAlloc = 256 * 1024; // 256K
            // keep udp at roughly the mtu, which is 1540 or something
            // letting it grow freaks out nio apparently.
            if (mChannel.isChunked())
                maxAlloc = 8192;
            
            ByteBuffer b = ByteBuffer.allocate(Math.min(Math.max(mToAlloc, 2 << 11), maxAlloc));
            // keep track of the max mount read during this read cycle
            // so we can be quicker about allocations during the next
            // time this socket reads.
            int read = mChannel.read(b);
            if (read < 0) {
                close();
                closed = true;
            }
            else {
                total += read;
            }
            if (read > 0) {
                mToAlloc = read * 2;
                b.limit(b.position());
                b.position(0);
                spit(b);
                if (b.remaining() != 0) {
                    Assert.assertTrue(pending == null);
//                    System.out.println("There was data remaining after this op: " + b.remaining());
                    pending = b;
                }
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
//        try {
//            throw new Exception();
//        }
//        catch (Exception ex) {
//            System.out.println("data callback set " + this);
//            ex.printStackTrace();
//        }
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
            mExceptionCallback.onCompleted(e);
        else
            e.printStackTrace();
    }
    
    private CompletedCallback mExceptionCallback;
    @Override
    public void setCompletedCallback(CompletedCallback callback) {
        mExceptionCallback = callback;
    }

    @Override
    public CompletedCallback getCompletedCallback() {
        return mExceptionCallback;
    }
    
    @Override
    public boolean isConnected() {
        return mChannel.isConnected();
    }
    
    @Override
    public boolean isOpen() {
        return mChannel.isConnected();
    }
    
    boolean mPaused = false;
    @Override
    public void pause() {
        if (mPaused)
            return;
        mPaused = true;
        try {
            mKey.interestOps(~SelectionKey.OP_READ & mKey.interestOps());
        }
        catch (Exception ex) {
        }
    }
    
    private void spitPending() {
        if (pending != null) {
//            System.out.println("p[ending spit");
            spit(pending);
//            System.out.println("pending now: " + pending.remaining());
            if (pending.remaining() == 0) {
                pending = null;
            }
        }
    }
    
    @Override
    public void resume() {
        if (!mPaused)
            return;
        mPaused = false;
        try {
            mKey.interestOps(SelectionKey.OP_READ | mKey.interestOps());
        }
        catch (Exception ex) {
        }
        spitPending();
    }
    
    @Override
    public boolean isPaused() {
        return mPaused;
    }
}
