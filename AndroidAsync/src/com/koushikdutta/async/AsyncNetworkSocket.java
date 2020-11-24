package com.koushikdutta.async;

import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.util.Allocator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
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
        allocator = new Allocator();
        mChannel = new SocketChannelWrapper(channel);
    }
    
    void attach(DatagramChannel channel) throws IOException {
        mChannel = new DatagramChannelWrapper(channel);
        // keep udp at roughly the mtu, which is 1540 or something
        // letting it grow freaks out nio apparently.
        allocator = new Allocator(8192);
    }
    
    ChannelWrapper getChannel() {
        return mChannel;
    }
    
    public void onDataWritable() {
        if (!mChannel.isChunked()) {
            // turn write off
            mKey.interestOps(~SelectionKey.OP_WRITE & mKey.interestOps());
        }
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
    
    private void handleRemaining(int remaining) throws IOException {
        if (!mKey.isValid())
            throw new IOException(new CancelledKeyException());
        if (remaining > 0) {
            // chunked channels should not fail
            // register for a write notification if a write fails
            // turn write on
            mKey.interestOps(SelectionKey.OP_WRITE | mKey.interestOps());
        }
        else {
            // turn write off
            mKey.interestOps(~SelectionKey.OP_WRITE & mKey.interestOps());
        }
    }
    private ByteBufferList pending = new ByteBufferList();
//    private ByteBuffer[] buffers = new ByteBuffer[8];

    Allocator allocator;
    int onReadable() {
        spitPending();
        // even if the socket is paused,
        // it may end up getting a queued readable event if it is
        // already in the selector's ready queue.
        if (mPaused)
            return 0;
        int total = 0;
        boolean closed = false;

//            ByteBufferList.obtainArray(buffers, Math.min(Math.max(mToAlloc, 2 << 11), maxAlloc));
        ByteBuffer b = allocator.allocate();
        // keep track of the max mount read during this read cycle
        // so we can be quicker about allocations during the next
        // time this socket reads.
        long read;
        try {
            read = mChannel.read(b);
        }
        catch (Exception e) {
            read = -1;
            closeInternal();
            reportEndPending(e);
            reportClose(e);
        }

        if (read < 0) {
            closeInternal();
            closed = true;
        }
        else {
            total += read;
        }
        if (read > 0) {
            allocator.track(read);
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
        else {
            ByteBufferList.reclaim(b);
        }

        if (closed) {
            reportEndPending(null);
            reportClose(null);
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

    private void closeInternal() {
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

    public InetAddress getLocalAddress() {
        return mChannel.getLocalAddress();
    }

    public int getLocalPort() {
        return mChannel.getLocalPort();
    }

    public Object getSocket() {
        return getChannel().getSocket();
    }

    @Override
    public String charset() {
        return null;
    }
}
