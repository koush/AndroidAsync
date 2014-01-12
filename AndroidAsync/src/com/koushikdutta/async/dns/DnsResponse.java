package com.koushikdutta.async.dns;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.Multimap;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
