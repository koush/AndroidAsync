package com.koushikdutta.async.http;

import android.net.Uri;
import android.text.TextUtils;

import com.koushikdutta.async.util.TaggedList;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by koush on 5/27/13.
 */
public class Multimap extends LinkedHashMap<String, List<String>> implements Iterable<NameValuePair> {
    public Multimap() {
    }

    protected List<String> newList() {
        return new ArrayList<String>();
    }

    public String getString(String name) {
        List<String> ret = get(name);
        if (ret == null || ret.size() == 0)
            return null;
        return ret.get(0);
    }

    public String getAllString(String name, String delimiter) {
        List<String> ret = get(name);
        if (ret == null || ret.size() == 0)
            return null;
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String value: ret) {
            if (!first)
                builder.append(delimiter);

            builder.append(value);
            first = false;
        }
        return builder.toString();
    }

    public List<String> ensure(String name) {
        List<String> ret = get(name);
        if (ret == null) {
            ret = newList();
            put(name, ret);
        }
        return ret;
    }

    public void add(String name, String value) {
        ensure(name).add(value);
    }

    public void put(String name, String value) {
        List<String> ret = newList();
        ret.add(value);
        put(name, ret);
    }

    public Multimap(List<NameValuePair> pairs) {
        for (NameValuePair pair: pairs)
            add(pair.getName(), pair.getValue());
    }

    public Multimap(Multimap m) {
        putAll(m);
    }

    public interface StringDecoder {
        public String decode(String s);
    }

    public static Multimap parse(String value, String delimiter, boolean unquote, StringDecoder decoder) {
        return parse(value, delimiter, "=", unquote, decoder);
    }

    public static Multimap parse(String value, String delimiter, String assigner, boolean unquote, StringDecoder decoder) {
        Multimap map = new Multimap();
        if (value == null)
            return map;
        String[] parts = value.split(delimiter);
        for (String part: parts) {
            String[] pair = part.split(assigner, 2);
            String key = pair[0].trim();
            // watch for empty string or trailing delimiter
            if (TextUtils.isEmpty(key))
                continue;
            String v = null;
            if (pair.length > 1)
                v = pair[1];
            if (v != null && unquote && v.endsWith("\"") && v.startsWith("\""))
                v = v.substring(1, v.length() - 1);
            if (v != null && decoder != null) {
                key = decoder.decode(key);
                v = decoder.decode(v);
            }
            map.add(key, v);
        }
        return map;
    }

    public static Multimap parseSemicolonDelimited(String header) {
        return parse(header, ";", true, null);
    }

    public static Multimap parseCommaDelimited(String header) {
        return parse(header, ",", true, null);
    }

    public static final StringDecoder QUERY_DECODER = new StringDecoder() {
        @Override
        public String decode(String s) {
            return Uri.decode(s);
        }
    };

    public static Multimap parseQuery(String query) {
        return parse(query, "&", false, QUERY_DECODER);
    }

    public static final StringDecoder URL_DECODER = new StringDecoder() {
        @Override
        public String decode(String s) {
            return URLDecoder.decode(s);
        }
    };

    public static Multimap parseUrlEncoded(String query) {
        return parse(query, "&", false, URL_DECODER);
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

    public Map<String, String> toSingleMap() {
        HashMap<String, String> ret = new HashMap<>();
        for (String key: keySet()) {
            ret.put(key, getString(key));
        }
        return ret;
    }
}
