package com.koushikdutta.async;

import android.os.Looper;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.PriorityQueue;

public class ByteBufferList {
    LinkedList<ByteBuffer> mBuffers = new LinkedList<ByteBuffer>();
    
    ByteOrder order = ByteOrder.BIG_ENDIAN;
    public ByteOrder order() {
        return order;
    }

    public ByteBufferList order(ByteOrder order) {
        this.order = order;
        return this;
    }

    public ByteBufferList() {
    }

    public ByteBufferList(ByteBuffer... b) {
        addAll(b);
    }

    public ByteBufferList(byte[] buf) {
        super();
        ByteBuffer b = ByteBuffer.wrap(buf);
        add(b);
    }

    public void addAll(ByteBuffer... bb) {
        for (ByteBuffer b: bb)
            add(b);
    }

    public byte[] getAllByteArray() {
        // fast path to return the contents of the first and only byte buffer,
        // if that's what we're looking for. avoids allocation.
        if (mBuffers.size() == 1 && mBuffers.peek().capacity() == remaining())
            return mBuffers.remove().array();

        byte[] ret = new byte[remaining()];
        get(ret);

        return ret;
    }

    public ByteBuffer[] getAllArray() {
        ByteBuffer[] ret = new ByteBuffer[mBuffers.size()];
        ret = mBuffers.toArray(ret);
        mBuffers.clear();
        remaining = 0;
        return ret;
    }

    public boolean isEmpty() {
        return remaining == 0;
    }

    private int remaining = 0;
    public int remaining() {
        return remaining;
    }
    
    public int getInt() {
        int ret = read(4).getInt();
        remaining -= 4;
        return ret;
    }
    
    public char getByteChar() {
        char ret = (char)read(1).get();
        remaining--;
        return ret;
    }
    
    public int getShort() {
        int ret = read(2).getShort();
        remaining -= 2;
        return ret;
    }
    
    public byte get() {
        byte ret = read(1).get();
        remaining--;
        return ret;
    }
    
    public long getLong() {
        long ret = read(8).getLong();
        remaining -= 8;
        return ret;
    }

    public void get(byte[] bytes) {
        get(bytes, 0, bytes.length);
    }

    public void get(byte[] bytes, int offset, int length) {
        if (remaining() < length)
            throw new IllegalArgumentException("length");

        int need = length;
        while (need > 0) {
            ByteBuffer b = mBuffers.peek();
            int read = Math.min(b.remaining(), need);
            b.get(bytes, offset, read);
            need -= read;
            offset += read;
            if (b.remaining() == 0) {
                ByteBuffer removed = mBuffers.remove();
                assert b == removed;
                reclaim(b);
            }
        }

        remaining -= length;
    }

    public void get(ByteBufferList into, int length) {
        if (remaining() < length)
            throw new IllegalArgumentException("length");
        int offset = 0;

        while (offset < length) {
            ByteBuffer b = mBuffers.remove();
            int remaining = b.remaining();

            if (remaining == 0) {
                reclaim(b);
                continue;
            }

            if (offset + remaining > length) {
                int need = length - offset;
                // this is shared between both
                ByteBuffer subset = obtain(need);
                subset.limit(need);
                b.get(subset.array(), 0, need);
                into.add(subset);
                mBuffers.add(0, b);
                break;
            }
            else {
                // this belongs to the new list
                into.add(b);
            }

            offset += remaining;
        }

        remaining -= length;
    }

    public void get(ByteBufferList into) {
        get(into, remaining());
    }

    public ByteBufferList get(int length) {
        ByteBufferList ret = new ByteBufferList();
        get(ret, length);
        return ret.order(order);
    }

    public ByteBuffer getAll() {
        read(remaining());
        return remove();
    }

    private ByteBuffer read(int count) {
        if (remaining() < count)
            throw new IllegalArgumentException("count");

        ByteBuffer first = mBuffers.peek();
        while (first != null && first.position() == first.limit()) {
            reclaim(mBuffers.remove());
            first = mBuffers.peek();
        }
        
        if (first == null) {
            return ByteBuffer.wrap(new byte[0]).order(order);
        }

        if (first.remaining() >= count) {
            return first.order(order);
        }
        else {
            // reallocate the count into a single buffer, and return it
            byte[] bytes = new byte[count];
            int offset = 0;
            ByteBuffer bb = null;
            while (offset < count) {
                if (bb != null)
                    reclaim(bb);
                bb = mBuffers.remove();
                int toRead = Math.min(count - offset, bb.remaining());
                bb.get(bytes, offset, toRead);
                offset += toRead;
            }
            assert bb != null;
            // if there was still data left in the last buffer we popped
            // toss it back into the head
            if (bb.position() < bb.limit())
                mBuffers.add(0, bb);
            ByteBuffer ret = ByteBuffer.wrap(bytes);
            mBuffers.add(0, ret);
            return ret.order(order);
        }
    }
    
    public void trim() {
        // this clears out buffers that are empty in the beginning of the list
        read(0);
    }
    
    public void add(ByteBuffer b) {
        if (b.remaining() <= 0) {
            reclaim(b);
            return;
        }
        addRemaining(b.remaining());
        mBuffers.add(b);
        trim();
    }
    
    public void add(int location, ByteBuffer b) {
        addRemaining(b.remaining());
        mBuffers.add(location, b);
    }

    private void addRemaining(int remaining) {
        if (this.remaining() >= 0)
            this.remaining += remaining;
    }

    public void clear() {
        mBuffers.clear();
        remaining = 0;
    }
    
    public ByteBuffer remove() {
        ByteBuffer ret = mBuffers.remove();
        remaining -= ret.remaining();
        return ret;
    }
    
    public int size() {
        return mBuffers.size();
    }

    public void spewString() {
        System.out.println(peekString());
    }

    // not doing toString as this is really nasty in the debugger...
    public String peekString() {
        StringBuilder builder = new StringBuilder();
        for (ByteBuffer bb: mBuffers) {
            builder.append(new String(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining()));
        }
        return builder.toString();
    }

    public String readString() {
        StringBuilder builder = new StringBuilder();
        while (mBuffers.size() > 0) {
            ByteBuffer bb = mBuffers.remove();
            builder.append(new String(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining()));
            reclaim(bb);
        }
        return builder.toString();
    }

    static PriorityQueue<ByteBuffer> reclaimed = new PriorityQueue<ByteBuffer>();
    private static PriorityQueue<ByteBuffer> getReclaimed() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread())
            return null;
        return reclaimed;
    }

    private static final int MAX_SIZE = 1024 * 1024;
    static int currentSize = 0;

    public static void reclaim(ByteBuffer b) {
        if (b.arrayOffset() != 0 || b.array().length != b.capacity()) {
            return;
        }
        if (b.capacity() < 8192)
            return;

        if (currentSize > MAX_SIZE) {
            return;
        }

        b.position(0);
        b.limit(b.capacity());
        currentSize += b.capacity();

        PriorityQueue<ByteBuffer> r = getReclaimed();
        if (r == null)
            return;

        synchronized (r) {
            r.add(b);
        }
    }

    public static ByteBuffer obtain(int size) {
        if (size <= 8192) {
            assert Thread.currentThread() != Looper.getMainLooper().getThread();
            PriorityQueue<ByteBuffer> r = getReclaimed();
            if (r != null) {
                synchronized (r) {
                    while (r.size() > 0) {
                        ByteBuffer ret = r.remove();
                        currentSize -= ret.capacity();
                        if (ret.capacity() >= size) {
                            return ret;
                        }
                    }
                }
            }
        }

        return ByteBuffer.allocate(Math.max(8192, size));
    }

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
}
