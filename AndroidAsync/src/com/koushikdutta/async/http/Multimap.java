package com.koushikdutta.async.http;

import android.net.Uri;
import com.koushikdutta.async.http.libcore.RawHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

/**
 * Created by koush on 5/27/13.
 */
public class Multimap extends Hashtable<String, List<String>> implements Iterable<NameValuePair> {
    public Multimap() {
    }

    public String getString(String name) {
        List<String> ret = get(name);
        if (ret == null || ret.size() == 0)
            return null;
        return ret.get(0);
    }

    public void add(String name, String value) {
        List<String> ret = get(name);
        if (ret == null) {
            ret = new ArrayList<String>();
            put(name, ret);
        }
        ret.add(value);
    }

    public void put(String name, String value) {
        ArrayList<String> ret = new ArrayList<String>();
        ret.add(value);
        put(name, ret);
    }

    public Multimap(RawHeaders headers) {
        headers.toMultimap().putAll(this);
    }

    public Multimap(List<NameValuePair> pairs) {
        for (NameValuePair pair: pairs)
            add(pair.getName(), pair.getValue());
    }

    public static Multimap parseHeader(String header) {
        Multimap map = new Multimap();
        String[] parts = header.split(";");
        for (String part: parts) {
            String[] pair = part.split("=", 2);
            String key = pair[0].trim();
            String v = null;
            if (pair.length > 1)
                v = pair[1];
            if (v != null && v.endsWith("\"") && v.startsWith("\""))
                v = v.substring(1, v.length() - 1);
            map.add(key, v);
        }
        return map;
    }

    public static Multimap parseHeader(RawHeaders headers, String header) {
        return parseHeader(headers.get(header));
    }

    public static Multimap parseQuery(String query) {
        Multimap map = new Multimap();
        String[] pairs = query.split("&");
        for (String p : pairs) {
            String[] pair = p.split("=", 2);
            if (pair.length == 0)
                continue;
            String name = Uri.decode(pair[0]);
            String value = null;
            if (pair.length == 2)
                value = Uri.decode(pair[1]);
            map.add(name, value);
        }
        return map;
    }

    public static Multimap parseUrlEncoded(String query) {
        Multimap map = new Multimap();
        String[] pairs = query.split("&");
        for (String p : pairs) {
            String[] pair = p.split("=", 2);
            if (pair.length == 0)
                continue;
            String name = URLDecoder.decode(pair[0]);
            String value = null;
            if (pair.length == 2)
                value = URLDecoder.decode(pair[1]);
            map.add(name, value);
        }
        return map;
    }

    @Override
    public Iterator<NameValuePair> iterator() {
        ArrayList<NameValuePair> ret = new ArrayList<NameValuePair>();
        for (String name: keySet()) {
            List<String> values = get(name);
            for (String value: values) {
                ret.add(new BasicNameValuePair(name, value));
            }
        }
        return ret.iterator();
    }
}
