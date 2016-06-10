package com.koushikdutta.async.dns;

import com.koushikdutta.async.AsyncDatagramSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Created by koush on 10/20/13.
 */
public class Dns {
    public static Future<DnsResponse> lookup(String host) {
        return lookup(AsyncServer.getDefault(), host, false, null);
    }

    private static int setFlag(int flags, int value, int offset) {
        return flags | (value << offset);
    }

    private static int setQuery(int flags) {
        return setFlag(flags, 0, 0);
    }

    private static int setRecursion(int flags) {
        return setFlag(flags, 1, 8);
    }

    private static void addName(ByteBuffer bb, String name) {
        String[] parts = name.split("\\.");
        for (String part: parts) {
            bb.put((byte)part.length());
            bb.put(part.getBytes());
        }
        bb.put((byte)0);
    }

    public static Future<DnsResponse> lookup(AsyncServer server, String host) {
        return lookup(server, host, false, null);
    }

    public static Cancellable multicastLookup(AsyncServer server, String host, FutureCallback<DnsResponse> callback) {
        return lookup(server, host, true, callback);
    }

    public static Cancellable multicastLookup(String host, FutureCallback<DnsResponse> callback) {
        return multicastLookup(AsyncServer.getDefault(), host, callback);
    }

    public static Future<DnsResponse> lookup(AsyncServer server, String host, final boolean multicast, final FutureCallback<DnsResponse> callback) {
        ByteBuffer packet = ByteBufferList.obtain(1024).order(ByteOrder.BIG_ENDIAN);
        short id = (short)new Random().nextInt();
        short flags = (short)setQuery(0);
        if (!multicast)
            flags = (short)setRecursion(flags);

        packet.putShort(id);
        packet.putShort(flags);
        // number questions
        packet.putShort(multicast ? (short)1 : (short)2);
        // number answer rr
        packet.putShort((short)0);
        // number authority rr
        packet.putShort((short)0);
        // number additional rr
        packet.putShort((short)0);

        addName(packet, host);
        // query
        packet.putShort(multicast ? (short)12 : (short)1);
        // request internet address
        packet.putShort((short)1);

        if (!multicast) {
            addName(packet, host);
            // AAAA query
            packet.putShort((short) 28);
            // request internet address
            packet.putShort((short)1);
        }

        packet.flip();


        try {
            final AsyncDatagramSocket dgram;
            // todo, use the dns server...
            if (!multicast) {
                dgram = server.connectDatagram(new InetSocketAddress("8.8.8.8", 53));
            }
            else {
//                System.out.println("multicast dns...");
                dgram = AsyncServer.getDefault().openDatagram(new InetSocketAddress(0), true);
                Field field = DatagramSocket.class.getDeclaredField("impl");
                field.setAccessible(true);
                Object impl = field.get(dgram.getSocket());
                Method method = impl.getClass().getDeclaredMethod("join", InetAddress.class);
                method.setAccessible(true);
                method.invoke(impl, InetAddress.getByName("224.0.0.251"));
                ((DatagramSocket)dgram.getSocket()).setBroadcast(true);
            }
            final SimpleFuture<DnsResponse> ret = new SimpleFuture<DnsResponse>() {
                @Override
                protected void cleanup() {
                    super.cleanup();
//                    System.out.println("multicast dns cleanup...");
                    dgram.close();
                }
            };
            dgram.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    try {
//                        System.out.println(dgram.getRemoteAddress());
                        DnsResponse response = DnsResponse.parse(bb);
//                        System.out.println(response);
                        response.source = dgram.getRemoteAddress();

                        if (!multicast) {
                            dgram.close();
                            ret.setComplete(response);
                        }
                        else {
                            callback.onCompleted(null, response);
                        }
                    }
                    catch (Exception e) {
                    }
                    bb.recycle();
                }
            });
            if (!multicast)
                dgram.write(new ByteBufferList(packet));
            else
                dgram.send(new InetSocketAddress("224.0.0.251", 5353), packet);
            return ret;
        }
        catch (Exception e) {
            SimpleFuture<DnsResponse> ret = new SimpleFuture<DnsResponse>();
            ret.setComplete(e);
            if (multicast)
                callback.onCompleted(e, null);
            return ret;
        }
    }
}
