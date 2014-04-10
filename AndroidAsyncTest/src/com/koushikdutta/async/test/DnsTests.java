package com.koushikdutta.async.test;

import com.koushikdutta.async.AsyncDatagramSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.dns.Dns;
import com.koushikdutta.async.dns.DnsResponse;
import com.koushikdutta.async.future.FutureCallback;

import junit.framework.TestCase;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by koush on 10/20/13.
 */
public class DnsTests extends TestCase {
    public void testLookup() throws Exception {
//        final Semaphore semaphore = new Semaphore(0);
//        Dns.lookup("google.com")
//        .setCallback(new FutureCallback<DnsResponse>() {
//            @Override
//            public void onCompleted(Exception e, DnsResponse result) {
//                semaphore.release();
//            }
//        });
//
//        semaphore.tryAcquire(1000000, TimeUnit.MILLISECONDS);
    }

    public void testMulticastLookup() throws Exception {
//        MulticastSocket socket = new MulticastSocket(5353);
//        socket.joinGroup(InetAddress.getByName("224.0.0.251"));
//        DatagramChannel channel = socket.getChannel();
//        assertNotNull(channel);

//        while (true) {
//            DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
//            socket.receive(packet);
//            System.out.println(new String(packet.getData()));
//        }

//        AsyncDatagramSocket dgram = AsyncServer.getDefault().openDatagram(new InetSocketAddress(5353), true);
//        ((DatagramSocket)dgram.getSocket()).setReuseAddress(true);
//        dgram.setDataCallback(new DataCallback() {
//            @Override
//            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
//                System.out.println(bb.readString());
//            }
//        });
//        ((DatagramSocket)dgram.getSocket()).setBroadcast(true);


//        final Semaphore semaphore = new Semaphore(0);
//        Dns.multicastLookup("_airplay._tcp.local", new FutureCallback<DnsResponse>() {
//            @Override
//            public void onCompleted(Exception e, DnsResponse result) {
////                semaphore.release();
//            }
//        });
//
//        semaphore.tryAcquire(1000000, TimeUnit.MILLISECONDS);
    }

    public void testNoDomain() throws Exception {
        AsyncServer server = new AsyncServer();

        try {
            final Semaphore semaphore = new Semaphore(0);
            server.connectSocket("www.clockworkmod-notfound.com", 8080, new ConnectCallback() {
                @Override
                public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                    assertTrue(ex instanceof UnknownHostException);
                    semaphore.release();
                }
            });
            assertTrue(semaphore.tryAcquire(5000, TimeUnit.MILLISECONDS));
        }
        finally {
            server.stop();
        }
    }
}
