package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

class SocketChannelWrapper extends ChannelWrapper {
    SocketChannel mChannel;

    @Override
    public int getLocalPort() {
        return mChannel.socket().getLocalPort();
    }

    SocketChannelWrapper(SocketChannel channel) throws IOException {
        super(channel);
        mChannel = channel;
    }
    @Override
    public int read(ByteBuffer buffer) throws IOException {
        return mChannel.read(buffer);
    }
    @Override
    public boolean isConnected() {
        return mChannel.isConnected();
    }
    @Override
    public int write(ByteBuffer src) throws IOException {
        return mChannel.write(src);
    }
    @Override
    public int write(ByteBuffer[] src) throws IOException {
        return (int)mChannel.write(src);
    }
    @Override
    public SelectionKey register(Selector sel) throws ClosedChannelException {
        return register(sel, SelectionKey.OP_CONNECT);
    }

    @Override
    public void shutdownOutput() {
        try {
            mChannel.socket().shutdownOutput();
        }
        catch (Exception e) {
        }
    }

    @Override
    public void shutdownInput() {
        try {
            mChannel.socket().shutdownInput();
        }
        catch (Exception e) {
        }
    }

    @Override
    public long read(ByteBuffer[] byteBuffers) throws IOException {
        return mChannel.read(byteBuffers);
    }

    @Override
    public long read(ByteBuffer[] byteBuffers, int i, int i2) throws IOException {
        return mChannel.read(byteBuffers, i, i2);
    }

    @Override
    public Object getSocket() {
        return mChannel.socket();
    }
}
