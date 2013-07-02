package com.koushikdutta.async.util;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Created by koush on 5/27/13.
 */
public class HashList<T> extends Hashtable<String, ArrayList<T>> {
    public HashList() {
    }

    public boolean contains(String key) {
        ArrayList<T> check = get(key);
        return check != null && check.size() > 0;
    }

    public void add(String key, T value) {
        ArrayList<T> ret = get(key);
        if (ret == null) {
            ret = new ArrayList<T>();
            put(key, ret);
        }
        ret.add(value);
    }
}
