package com.koushikdutta.async.dns;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.Multimap;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Created by koush on 10/20/13.
 */
public class DnsResponse {
    public ArrayList<InetAddress> addresses = new ArrayList<InetAddress>();
    public ArrayList<String> names = new ArrayList<String>();
    public Multimap txt = new Multimap();
    public InetSocketAddress source;

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

    public static DnsResponse parse(ByteBufferList bb) {
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
//                e.printStackTrace();
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
//                e.printStackTrace();
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
//                e.printStackTrace();
            }
        }

        return response;
    }

    void parseTxt(ByteBufferList bb) {
        while (bb.hasRemaining()) {
            int length = (int)bb.get() & 0x00FF;
            byte [] bytes = new byte[length];
            bb.get(bytes);
            String string = new String(bytes);
            String[] pair = string.split("=");
            txt.add(pair[0], pair[1]);
        }
    }

    @Override
    public String toString() {
        String ret = "addresses:\n";
        for (InetAddress address: addresses)
            ret += address.toString() + "\n";
        ret += "names:\n";
        for (String name: names)
            ret += name + "\n";
        return ret;
    }
}
