package com.koushikdutta.async.dns;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

/**
 * Created by koush on 10/20/13.
 */
public class DnsResponse {
    public ArrayList<InetAddress> addresses = new ArrayList<InetAddress>();
    public ArrayList<String> names = new ArrayList<String>();
    public InetSocketAddress source;

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
