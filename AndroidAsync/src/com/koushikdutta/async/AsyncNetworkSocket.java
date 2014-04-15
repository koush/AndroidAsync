package com.koushikdutta.async;

import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class AsyncNetworkSocket implements AsyncSocket {
    AsyncNetworkSocket() {
    }

    @Override
    public void end() {
        mChannel.shutdownOutput();
    }

    public boolean isChunked() {
        return mChannel.isChunked();
    }

    InetSocketAddress socketAddress;
    void attach(SocketChannel channel, InetSocketAddress socketAddress) throws IOException {
        this.socketAddress = socketAddress;
        maxAlloc = 256 * 1024; // 256K
        mChannel = new SocketChannelWrapper(channel);
    }
    
    void attach(DatagramChannel channel) throws IOException {
        mChannel = new DatagramChannelWrapper(channel);
        // keep udp at roughly the mtu, which is 1540 or something
        // letting it grow freaks out nio apparently.
        maxAlloc = 8192;
    }
    
    ChannelWrapper getChannel() {
        return mChannel;
    }
    
    public void onDataWritable() {
//        assert mWriteableHandler != null;
        if (mWriteableHandler != null)
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
            assert !mChannel.isChunked();
            return;
        }

        try {
            int before = list.remaining();
            ByteBuffer[] arr = list.getAllArray();
            mChannel.write(arr);
            list.addAll(arr);
            handleRemaining(list.remaining());
            mServer.onDataSent(before - list.remaining());
        }
        catch (IOException e) {
            closeInternal();
            reportEndPending(e);
            reportClose(e);
        }
    }
    
    private void handleRemaining(int remaining) {
        if (remaining > 0) {
            // chunked channels should not fail
            assert !mChannel.isChunked();
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
                assert !mChannel.isChunked();
                return;
            }

            // keep writing until the the socket can't write any more, or the
            // data is exhausted.
            int before = b.remaining();
            mChannel.write(b);
            handleRemaining(b.remaining());
            mServer.onDataSent(before - b.remaining());
        }
        catch (IOException ex) {
            closeInternal();
            reportEndPending(ex);
            reportClose(ex);
        }
    }

    private ByteBufferList pending = new ByteBufferList();
//    private ByteBuffer[] buffers = new ByteBuffer[8];

    int maxAlloc;
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

//            ByteBufferList.obtainArray(buffers, Math.min(Math.max(mToAlloc, 2 << 11), maxAlloc));
            ByteBuffer b = ByteBufferList.obtain(Math.min(Math.max(mToAlloc, 2 << 11), maxAlloc));
            // keep track of the max mount read during this read cycle
            // so we can be quicker about allocations during the next
            // time this socket reads.
            long read = mChannel.read(b);
            if (read < 0) {
                closeInternal();
                closed = true;
            }
            else {
                total += read;
            }
            if (read > 0) {
                mToAlloc = (int)read * 2;
                b.flip();
//                for (int i = 0; i < buffers.length; i++) {
//                    ByteBuffer b = buffers[i];
//                    buffers[i] = null;
//                    b.flip();
//                    pending.add(b);
//                }
                pending.add(b);
                Util.emitAllData(this, pending);
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
    protected void reportClose(Exception e) {
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
        else if (e != null) {
            Log.e("NIO", "Unhandled exception", e);
        }
    }
    boolean mEndReported;
    Exception mPendingEndException;
    void reportEndPending(Exception e) {
        if (pending.hasRemaining()) {
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
        if (pending.hasRemaining()) {
            Util.emitAllData(this, pending);
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


    public InetSocketAddress getRemoteAddress() {
        return socketAddress;
    }
    
    public int getLocalPort() {
        return mChannel.getLocalPort();
    }

    public Object getSocket() {
        return getChannel().getSocket();
    }
}
