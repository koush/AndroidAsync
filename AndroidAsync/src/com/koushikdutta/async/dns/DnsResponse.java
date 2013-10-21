package com.koushikdutta.async.dns;

import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Created by koush on 10/20/13.
 */
public class DnsResponse {
    ArrayList<InetAddress> addresses = new ArrayList<InetAddress>();
    ArrayList<String> names = new ArrayList<String>();

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
