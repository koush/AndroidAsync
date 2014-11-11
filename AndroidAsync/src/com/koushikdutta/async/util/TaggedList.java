package com.koushikdutta.async.util;

import java.util.ArrayList;

public class TaggedList<T> extends ArrayList<T> {
    private Object tag;

    public synchronized <V> V tag() {
        return (V)tag;
    }

    public synchronized <V> void tag(V tag) {
        this.tag = tag;
    }

    public synchronized <V> void tagNull(V tag) {
        if (this.tag == null)
            this.tag = tag;
    }
}