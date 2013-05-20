package com.koushikdutta.async;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

class DatagramChannelWrapper extends ChannelWrapper {
    DatagramChannel mChannel;
    
    @Override
    public int getLocalPort() {
        return mChannel.socket().getLocalPort();
    }

    InetSocketAddress address;
    public InetSocketAddress getRemoteAddress() {
        return address;
    }
    
    public void disconnect() throws IOException {
        mChannel.disconnect();
    }
    
    DatagramChannelWrapper(DatagramChannel channel) throws IOException {
        super(channel);
        mChannel = channel;
    }
    @Override
    public int read(ByteBuffer buffer) throws IOException {
        if (!isConnected()) {
            int position = buffer.position();
            address = (InetSocketAddress)mChannel.receive(buffer);
            if (address == null)
                return -1;
            return buffer.position() - position;
        }
        address = null;
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
