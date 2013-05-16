package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class AsyncDatagramSocket extends AsyncNetworkSocket {
    public void disconnect() throws IOException {
//        ((DatagramChannelWrapper)getChannel()).disconnect();
    }
    
    public void connect(SocketAddress address) throws IOException {
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
            close();
            reportEndPending(e);
            //reportClose(e);
        }

    }
    public void send(final SocketAddress address, final ByteBuffer buffer) {
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
            ((DatagramChannelWrapper)getChannel()).mChannel.send(buffer, address);
        }
        catch (IOException e) {
            close();
            reportEndPending(e);
            //reportClose(e);
        }
    }
}
