package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

class ServerSocketChannelWrapper extends ChannelWrapper {
    ServerSocketChannel mChannel;

    @Override
    public void shutdownOutput() {
    }

    @Override
    public void shutdownInput() {
    }

    @Override
    public InetAddress getLocalAddress() {
        return mChannel.socket().getInetAddress();
    }

    @Override
    public int getLocalPort() {
        return mChannel.socket().getLocalPort();
    }

    ServerSocketChannelWrapper(ServerSocketChannel channel) throws IOException {
        super(channel);
        mChannel = channel;
    }

    @Override
    public int read(ByteBuffer buffer) throws IOException {
        final String msg = "Can't read ServerSocketChannel";
        throw new IOException(msg);
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        final String msg = "Can't write ServerSocketChannel";
        throw new IOException(msg);
    }

    @Override
    public SelectionKey register(Selector sel) throws ClosedChannelException {
        return mChannel.register(sel, SelectionKey.OP_ACCEPT);
    }

    @Override
    public int write(ByteBuffer[] src) throws IOException {
        final String msg = "Can't write ServerSocketChannel";
        throw new IOException(msg);
    }

    @Override
    public long read(ByteBuffer[] byteBuffers) throws IOException {
        final String msg = "Can't read ServerSocketChannel";
        throw new IOException(msg);
    }

    @Override
    public long read(ByteBuffer[] byteBuffers, int i, int i2) throws IOException {
        final String msg = "Can't read ServerSocketChannel";
        throw new IOException(msg);
    }

    @Override
    public Object getSocket() {
        return mChannel.socket();
    }
}
