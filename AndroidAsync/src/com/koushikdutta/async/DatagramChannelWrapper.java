package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

class DatagramChannelWrapper extends ChannelWrapper {
    DatagramChannel mChannel;

    DatagramChannelWrapper(DatagramChannel channel) throws IOException {
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
    public SelectionKey register(Selector sel, int ops) throws ClosedChannelException {
        return mChannel.register(sel, ops);
    }
    @Override
    public boolean isChunked() {
        return true;
    }
    @Override
    public SelectionKey register(Selector sel) throws ClosedChannelException {
        return register(sel, SelectionKey.OP_READ);
    }
}
