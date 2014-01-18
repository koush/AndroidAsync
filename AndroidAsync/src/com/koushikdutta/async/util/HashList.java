package com.koushikdutta.async.util;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Created by koush on 5/27/13.
 */
public class HashList<T> {
    @SuppressWarnings("serial")
    class TaggedList extends ArrayList<T> {
        Object tag;
    }
    Hashtable<String, TaggedList> internal = new Hashtable<String, TaggedList>();

    public HashList() {
    }

    public synchronized Object tag(String key) {
        TaggedList list = internal.get(key);
        if (list == null)
            return null;
        return list.tag;
    }

    public synchronized <V> void tag(String key, V tag) {
        TaggedList list = internal.get(key);
        if (list == null) {
            list = new TaggedList();
            internal.put(key, list);
        }
        list.tag = tag;
    }

    public synchronized ArrayList<T> remove(String key) {
        return internal.remove(key);
    }

    public synchronized int size() {
        return internal.size();
    }

    public synchronized ArrayList<T> get(String key) {
        return internal.get(key);
    }

    synchronized public boolean contains(String key) {
        ArrayList<T> check = get(key);
        return check != null && check.size() > 0;
    }

    synchronized public void add(String key, T value) {
        ArrayList<T> ret = get(key);
        if (ret == null) {
            TaggedList put = new TaggedList();
            ret = put;
            internal.put(key, put);
        }
        ret.add(value);
    }

    synchronized public Object removeItem(String key, T value) {
        TaggedList values = internal.get(key);
        if (values == null)
            return null;

        values.remove(value);
        if (values.size() == 0) {
            internal.remove(key);
            return values.tag;
        }
        return null;
    }
}
