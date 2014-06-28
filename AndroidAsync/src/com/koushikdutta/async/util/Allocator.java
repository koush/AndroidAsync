package com.koushikdutta.async.util;

import com.koushikdutta.async.ByteBufferList;

import java.nio.ByteBuffer;

/**
 * Created by koush on 6/28/14.
 */
public class Allocator {
    final int maxAlloc;
    int currentAlloc = 0;

    public Allocator(int maxAlloc) {
        this.maxAlloc = maxAlloc;
    }

    public Allocator() {
        maxAlloc = 256 * 1024;
    }

    public ByteBuffer allocate() {
        return ByteBufferList.obtain(Math.min(Math.max(currentAlloc, 2 << 11), maxAlloc));
    }

    public void track(long read) {
        currentAlloc = (int)read * 2;
    }

    public int getMaxAlloc() {
        return maxAlloc;
    }

    public void setCurrentAlloc(int currentAlloc) {
        this.currentAlloc = currentAlloc;
    }
}

