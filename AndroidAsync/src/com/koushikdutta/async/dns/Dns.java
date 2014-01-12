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

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
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

    private static String parseName(ByteBufferList bb, ByteBuffer backReference) {
        bb.order(ByteOrder.BIG_ENDIAN);
        String ret = "";

        int len;
        while (0 != (len = bb.get() & 0x00FF)) {
            // compressed
            if ((len & 0x00c0) == 0x00c0) {
                int offset = ((len & ~0xFFFFFFc0) << 8) | (bb.get() & 0x00FF);
                if (ret.length() > 0)
                    ret += ".";
                ByteBufferList sub = new ByteBufferList();
                ByteBuffer duplicate = backReference.duplicate();
                duplicate.get(new byte[offset]);
                sub.add(duplicate);
                return ret + parseName(sub, backReference);
            }

            byte[] bytes = new byte[len];
            bb.get(bytes);
            if (ret.length() > 0)
                ret += ".";
            ret += new String(bytes);
        }

        return ret;
    }

    private static InetAddress parseAddress(ByteBufferList bb) {
        return null;
    }

    private static DnsResponse parse(ByteBufferList bb) {
        ByteBuffer b = bb.getAll();
        bb.add(b.duplicate());
        // naive parsing...
        bb.order(ByteOrder.BIG_ENDIAN);

        // id
        bb.getShort();
        // flags
        bb.getShort();

        // number questions
        int questions = bb.getShort();
        // number answer rr
        int answers = bb.getShort();
        // number authority rr
        int authorities = bb.getShort();
        // number additional rr
        int additionals = bb.getShort();

        for (int i = 0; i < questions; i++) {
            parseName(bb, b);
            // type
            bb.getShort();
            // class
            bb.getShort();
        }

        DnsResponse response = new DnsResponse();
        for (int i = 0; i < answers; i++) {
            String name = parseName(bb, b);
            // type
            int type = bb.getShort();
            // class
            int clazz = bb.getShort();
            // ttl
            int ttl = bb.getInt();
            // length of address
            int length = bb.getShort();
            try {
                if (type == 1) {
                    // data
                    byte[] data = new byte[length];
                    bb.get(data);
                    response.addresses.add(InetAddress.getByAddress(data));
                }
                else if (type == 0x000c) {
                    response.names.add(parseName(bb, b));
                }
                else if (type == 16) {
                    ByteBufferList txt = new ByteBufferList();
                    bb.get(txt, length);
                    response.parseTxt(txt);
                }
                else {
                    bb.get(new byte[length]);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        // authorities
        for (int i = 0; i < authorities; i++) {
            String name = parseName(bb, b);
            // type
            int type = bb.getShort();
            // class
            int clazz = bb.getShort();
            // ttl
            int ttl = bb.getInt();
            // length of address
            int length = bb.getShort();
            try {
                bb.get(new byte[length]);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        // additionals
        for (int i = 0; i < additionals; i++) {
            String name = parseName(bb, b);
            // type
            int type = bb.getShort();
            // class
            int clazz = bb.getShort();
            // ttl
            int ttl = bb.getInt();
            // length of address
            int length = bb.getShort();
            try {
                if (type == 16) {
                    ByteBufferList txt = new ByteBufferList();
                    bb.get(txt, length);
                    response.parseTxt(txt);
                }
                else {
                    bb.get(new byte[length]);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return response;
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
                dgram = AsyncServer.getDefault().openDatagram(new InetSocketAddress(5353), true);
                Field field = DatagramSocket.class.getDeclaredField("impl");
                field.setAccessible(true);
                Object impl = field.get(dgram.getSocket());
                Method method = impl.getClass().getMethod("join", InetAddress.class);
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
                        DnsResponse response = parse(bb);
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
                dgram.write(packet);
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
