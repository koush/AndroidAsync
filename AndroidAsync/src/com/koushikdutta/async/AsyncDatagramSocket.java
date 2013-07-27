package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class AsyncDatagramSocket extends AsyncNetworkSocket {
    public void disconnect() throws IOException {
        socketAddress = null;
        ((DatagramChannelWrapper)getChannel()).disconnect();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        if (isOpen())
            return super.getRemoteAddress();
        return ((DatagramChannelWrapper)getChannel()).getRemoteAddress();
    }

    public void connect(InetSocketAddress address) throws IOException {
        socketAddress = address;
        ((DatagramChannelWrapper)getChannel()).mChannel.connect(address);
    }

    public void send(final String host, final int port, final ByteBuffer buffer) {
        if (getServer().getAffinity() != Thread.currentThread()) {
            getServer().run(new Runnable() {
                @Override
                public void run() {
                    send(host, port, buffer);
                }
            });
            return;
        }

        try {
            ((DatagramChannelWrapper)getChannel()).mChannel.send(buffer, new InetSocketAddress(host, port));
        }
        catch (IOException e) {
//            close();
//            reportEndPending(e);
//            reportClose(e);
        }

    }
    public void send(final InetSocketAddress address, final ByteBuffer buffer) {
        if (getServer().getAffinity() != Thread.currentThread()) {
            getServer().run(new Runnable() {
                @Override
                public void run() {
                    send(address, buffer);
                }
            });
            return;
        }

        try {
            int sent = ((DatagramChannelWrapper)getChannel()).mChannel.send(buffer, new InetSocketAddress(address.getHostName(), address.getPort()));
        }
        catch (IOException e) {
//            Log.e("SEND", "send error", e);
//            close();
//            reportEndPending(e);
//            reportClose(e);
        }
    }
}
