package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;

abstract class ChannelWrapper implements ReadableByteChannel, ScatteringByteChannel {
    private AbstractSelectableChannel mChannel;
    ChannelWrapper(AbstractSelectableChannel channel) throws IOException {
        channel.configureBlocking(false);
        mChannel = channel;
    }

    public abstract void shutdownInput();
    public abstract void shutdownOutput();
    
    public abstract boolean isConnected();
    
    public abstract int write(ByteBuffer src) throws IOException;
    public abstract int write(ByteBuffer[] src) throws IOException;

    // register for default events appropriate for this channel
    public abstract SelectionKey register(Selector sel) throws ClosedChannelException;

    public SelectionKey register(Selector sel, int ops) throws ClosedChannelException {
        return mChannel.register(sel, ops);
    }

    public boolean isChunked() {
        return false;
    }
    
    @Override
    public boolean isOpen() {
        return mChannel.isOpen();
    }
    
    @Override
    public void close() throws IOException {
       mChannel.close();
    }
    
    public abstract int getLocalPort();
    public abstract InetAddress getLocalAddress();
    public abstract Object getSocket();
}
