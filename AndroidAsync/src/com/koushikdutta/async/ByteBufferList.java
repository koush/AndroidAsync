package com.koushikdutta.async;

import junit.framework.Assert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.LinkedList;

public class ByteBufferList implements Iterable<ByteBuffer> {
    LinkedList<ByteBuffer> mBuffers = new LinkedList<ByteBuffer>();
    
    ByteOrder order = ByteOrder.BIG_ENDIAN;
    public ByteOrder order() {
        return order;
    }
    
    public ByteBufferList order(ByteOrder order) {
        this.order = order;
        return this;
    }

    public ByteBuffer peek() {
        remaining = -1;
        return mBuffers.peek();
    }

    public ByteBufferList() {
    }

    public ByteBufferList(ByteBuffer... b) {
        for (ByteBuffer bb: b)
            add(bb);
    }

    public ByteBufferList(byte[] buf) {
        super();
        ByteBuffer b = ByteBuffer.wrap(buf);
        add(b);
    }

    public ByteBuffer[] toArray() {
        remaining = -1;
        ByteBuffer[] ret = new ByteBuffer[mBuffers.size()];
        ret = mBuffers.toArray(ret);
        return ret;
    }

    int remaining = -1;
    public int remaining() {
        if (remaining >= 0) {
            return remaining;
        }
        remaining = 0;
        for (ByteBuffer bb: mBuffers) {
            remaining += bb.remaining();
        }
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
        read(bytes.length).get(bytes);
        remaining -= bytes.length;
    }

    public void get(ByteBufferList into, int length) {
        if (remaining() < length)
            throw new IllegalArgumentException("length");
        int offset = 0;
        for (ByteBuffer b: mBuffers) {
            int remaining = b.remaining();

            if (remaining == 0)
                continue;
            // done
            if (offset > length)
                break;

            if (offset + remaining > length) {
                int need = length - offset;
                // this is shared between both
                into.add(ByteBuffer.wrap(b.array(), b.arrayOffset() + b.position(), need));
                b.position(b.position() + need);
            }
            else {
                // this belongs to the new list
                into.add(ByteBuffer.wrap(b.array(), b.arrayOffset() + b.position(), remaining));
                b.position(b.limit());
            }

            offset += remaining;
        }

        remaining -= length;
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
            mBuffers.remove();
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
                bb = mBuffers.remove();
                int toRead = Math.min(count - offset, bb.remaining());
                bb.get(bytes, offset, toRead);
                offset += toRead;
            }
            Assert.assertNotNull(bb);
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
        if (b.remaining() <= 0)
            return;
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
    
    public void add(ByteBufferList b) {
        if (b.remaining() <= 0)
            return;
        addRemaining(b.remaining());
        mBuffers.addAll(b.mBuffers);
        trim();
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

    @Override
    public Iterator<ByteBuffer> iterator() {
        remaining = -1;
        return mBuffers.iterator();
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
        String ret = peekString();
        clear();
        return ret;
    }
}
