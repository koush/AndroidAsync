package com.koushikdutta.async;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;

import junit.framework.Assert;

public class ByteBufferList implements Iterable<ByteBuffer> {
    LinkedList<ByteBuffer> mBuffers = new LinkedList<ByteBuffer>();
    
    public ByteBuffer peek() {
        return mBuffers.peek();
    }

    public ByteBufferList() {
    }
    
    public ByteBuffer[] toArray() {
        ByteBuffer[] ret = new ByteBuffer[mBuffers.size()];
        ret = mBuffers.toArray(ret);
        return ret;
    }
    
    public int remaining() {
        int ret = 0;
        for (ByteBuffer bb: mBuffers) {
            ret += bb.remaining();
        }
        return ret;
    }
    
    public int getInt() {
        return read(4).getInt();
    }
    
    public char getByteChar() {
        return (char)read(1).get();
    }
    
    public int getShort() {
        return read(2).getShort();
    }
    
    public byte get() {
        return read(1).get();
    }
    
    public long getLong() {
        return read(8).getLong();
    }
    
    public void get(byte[] bytes) {
        read(bytes.length).get(bytes);
    }
    
    public ByteBufferList get(int length) {
        Assert.assertTrue(remaining() >= length);
        ByteBufferList ret = new ByteBufferList();
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
                ret.add(ByteBuffer.wrap(b.array(), b.arrayOffset() + b.position(), need));
                b.position(b.position() + need);
            }
            else {
                // this belongs to the new list
                ret.add(ByteBuffer.wrap(b.array(), b.arrayOffset() + b.position(), remaining));
                b.position(b.limit());
            }
            
            offset += remaining;
        }
        
        return ret;
    }

    public ByteBuffer read(int count) {
        Assert.assertTrue(count <= remaining());
        
        ByteBuffer first = mBuffers.peek();
        while (first != null && first.position() == first.limit()) {
            mBuffers.remove();
            first = mBuffers.peek();
        }
        
        if (first == null) {
            return ByteBuffer.wrap(new byte[0]);
        }

        if (first.remaining() >= count) {
            return first;
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
            return ret;
        }
    }
    
    public void trim() {
        // this clears out buffers that are empty in the beginning of the list
        read(0);
        if (remaining() == 0)
            mBuffers = new LinkedList<ByteBuffer>();
    }
    
    public void add(ByteBuffer b) {
        if (b.remaining() <= 0)
            return;
        mBuffers.add(b);
        trim();
    }
    
    public void add(int location, ByteBuffer b) {
        mBuffers.add(location, b);
    }
    
    public void add(ByteBufferList b) {
        if (b.remaining() <= 0)
            return;
        mBuffers.addAll(b.mBuffers);
        trim();
    }
    
    public void clear() {
        mBuffers.clear();
    }
    
    public ByteBuffer remove() {
        return mBuffers.remove();
    }
    
    public int size() {
        return mBuffers.size();
    }

    @Override
    public Iterator<ByteBuffer> iterator() {
        return mBuffers.iterator();
    }
    
    public void spewString() {
        for (ByteBuffer bb: mBuffers) {
            try {
                String s = new String(bb.array(), bb.arrayOffset() + bb.position(), bb.limit());
                System.out.println(s);
            }
            catch (Exception e) {
                e.printStackTrace();
                
            }
        }
    }

    // not doing toString as this is really nasty in the debugger...
    public String getString() {
        StringBuilder builder = new StringBuilder();
        for (ByteBuffer bb: this) {
            builder.append(new String(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining()));
        }
        return builder.toString();
    }
}
