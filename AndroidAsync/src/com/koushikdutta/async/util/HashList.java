package com.koushikdutta.async.util;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Set;

/**
 * Created by koush on 5/27/13.
 */
public class HashList<T> {
    Hashtable<String, TaggedList<T>> internal = new Hashtable<String, TaggedList<T>>();

    public HashList() {
    }

    public Set<String> keySet() {
        return internal.keySet();
    }

    public synchronized <V> V tag(String key) {
        TaggedList<T> list = internal.get(key);
        if (list == null)
            return null;
        return list.tag();
    }

    public synchronized <V> void tag(String key, V tag) {
        TaggedList<T> list = internal.get(key);
        if (list == null) {
            list = new TaggedList<T>();
            internal.put(key, list);
        }
        list.tag(tag);
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
            TaggedList<T> put = new TaggedList<T>();
            ret = put;
            internal.put(key, put);
        }
        ret.add(value);
    }

    synchronized public T pop(String key) {
        TaggedList<T> values = internal.get(key);
        if (values == null)
            return null;
        if (values.size() == 0)
            return null;
        return values.remove(values.size() - 1);
    }

    synchronized public boolean removeItem(String key, T value) {
        TaggedList<T> values = internal.get(key);
        if (values == null)
            return false;

        values.remove(value);
        return values.size() == 0;
    }
}
