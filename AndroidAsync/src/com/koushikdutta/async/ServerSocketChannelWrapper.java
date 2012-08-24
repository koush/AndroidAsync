package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

import junit.framework.Assert;

class ServerSocketChannelWrapper extends ChannelWrapper {
    ServerSocketChannel mChannel;

    ServerSocketChannelWrapper(ServerSocketChannel channel) throws IOException {
        super(channel);
        mChannel = channel;
    }

    @Override
    public int read(ByteBuffer buffer) throws IOException {
        final String msg = "Can't read ServerSocketChannel";
        Assert.fail(msg);
        throw new IOException(msg);
    }

    @Override
    public boolean isConnected() {
        Assert.fail("ServerSocketChannel is never connected");
        return false;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        final String msg = "Can't write ServerSocketChannel";
        Assert.fail(msg);
        throw new IOException(msg);

    }

    @Override
    public SelectionKey register(Selector sel) throws ClosedChannelException {
        return mChannel.register(sel, SelectionKey.OP_ACCEPT);
    }

    @Override
    public int write(ByteBuffer[] src) throws IOException {
        final String msg = "Can't write ServerSocketChannel";
        Assert.fail(msg);
        throw new IOException(msg);
    }
}
