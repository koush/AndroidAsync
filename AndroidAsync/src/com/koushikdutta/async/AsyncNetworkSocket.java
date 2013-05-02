package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import junit.framework.Assert;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;

public class AsyncNetworkSocket implements AsyncSocket {
    AsyncNetworkSocket() {
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
    private SelectionKey mKey;
    private AsyncServer mServer;
    
    void setup(AsyncServer server, SelectionKey key) {
        mServer = server;
        mKey = key;
    }
    
    @Override
    public void write(final ByteBufferList list) {
        if (mServer.getAffinity() != Thread.currentThread()) {
            mServer.run(new Runnable() {
                @Override
                public void run() {
                    write(list);
                }
            });
            return;
        }
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
            reportEndPending(e);
            reportClose(e);
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
    public void write(final ByteBuffer b) {
        if (mServer.getAffinity() != Thread.currentThread()) {
            mServer.run(new Runnable() {
                @Override
                public void run() {
                    write(b);
                }
            });
            return;
        }
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
            reportEndPending(ex);
            reportClose(ex);
        }
    }

    private ByteBufferList pending;
    
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
                closeInternal();
                closed = true;
            }
            else {
                total += read;
            }
            if (read > 0) {
                mToAlloc = read * 2;
                b.limit(b.position());
                b.position(0);
                ByteBufferList list = new ByteBufferList(b);
                Util.emitAllData(this, list);
                if (b.remaining() != 0) {
                    Assert.assertTrue(pending == null);
                    pending = list;
                }
            }

            if (closed) {
                reportEndPending(null);
                reportClose(null);
            }
        }
        catch (Exception e) {
            closeInternal();
            reportEndPending(e);
            reportClose(e);
        }
        
        return total;
    }
    
    boolean closeReported;
    private void reportClose(Exception e) {
        if (closeReported)
            return;
        closeReported = true;
        if (mClosedHander != null) {
            mClosedHander.onCompleted(e);
            mClosedHander = null;
        }
    }

    @Override
    public void close() {
        closeInternal();
        reportClose(null);
    }

    public void closeInternal() {
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

    CompletedCallback mClosedHander;
    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mClosedHander = handler;       
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mClosedHander;
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mWriteableHandler;
    }

    void reportEnd(Exception e) {
        if (mEndReported)
            return;
        mEndReported = true;
        if (mCompletedCallback != null)
            mCompletedCallback.onCompleted(e);
        else if (e != null)
            e.printStackTrace();
    }
    boolean mEndReported;
    Exception mPendingEndException;
    void reportEndPending(Exception e) {
        if (pending != null) {
            mPendingEndException = e;
            return;
        }
        reportEnd(e);
    }
    
    private CompletedCallback mCompletedCallback;
    @Override
    public void setEndCallback(CompletedCallback callback) {
        mCompletedCallback = callback;
    }

    @Override
    public CompletedCallback getEndCallback() {
        return mCompletedCallback;
    }

    @Override
    public boolean isOpen() {
        return mChannel.isConnected() && mKey.isValid();
    }
    
    boolean mPaused = false;
    @Override
    public void pause() {
        if (mServer.getAffinity() != Thread.currentThread()) {
            mServer.run(new Runnable() {
                @Override
                public void run() {
                    pause();
                }
            });
            return;
        }
        
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
            Util.emitAllData(this, pending);
            if (pending.remaining() == 0) {
                pending = null;
            }
        }
    }
    
    @Override
    public void resume() {
        if (mServer.getAffinity() != Thread.currentThread()) {
            mServer.run(new Runnable() {
                @Override
                public void run() {
                    resume();
                }
            });
            return;
        }
        
        if (!mPaused)
            return;
        mPaused = false;
        try {
            mKey.interestOps(SelectionKey.OP_READ | mKey.interestOps());
        }
        catch (Exception ex) {
        }
        spitPending();
        if (!isOpen())
            reportEndPending(mPendingEndException);
    }
    
    @Override
    public boolean isPaused() {
        return mPaused;
    }

    @Override
    public AsyncServer getServer() {
        return mServer;
    }
}
