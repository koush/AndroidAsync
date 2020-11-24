package com.koushikdutta.async;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Looper;

import com.koushikdutta.async.util.ArrayDeque;
import com.koushikdutta.async.util.Charsets;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.PriorityQueue;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class ByteBufferList {
    ArrayDeque<ByteBuffer> mBuffers = new ArrayDeque<ByteBuffer>();
    
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

    public ByteBufferList addAll(ByteBuffer... bb) {
        for (ByteBuffer b: bb)
            add(b);
        return this;
    }

    public ByteBufferList addAll(ByteBufferList... bb) {
        for (ByteBufferList b: bb)
            b.get(this);
        return this;
    }

    public byte[] getBytes(int length) {
        byte[] ret = new byte[length];
        get(ret);
        return ret;
    }

    public byte[] getAllByteArray() {
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

    public boolean hasRemaining() {
        return remaining() > 0;
    }

    public short peekShort() {
        return read(2).getShort(mBuffers.peekFirst().position());
    }

    public byte peek() {
        return read(1).get(mBuffers.peekFirst().position());
    }

    public int peekInt() {
        return read(4).getInt(mBuffers.peekFirst().position());
    }

    public long peekLong() {
        return read(8).getLong(mBuffers.peekFirst().position());
    }

    public byte[] peekBytes(int size) {
        byte[] ret = new byte[size];
        read(size).get(ret, mBuffers.peekFirst().position(), ret.length);
        return ret;
    }

    public ByteBufferList skip(int length) {
        get(null, 0, length);
        return this;
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
    
    public short getShort() {
        short ret = read(2).getShort();
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
            if (bytes != null){
                b.get(bytes, offset, read);
            } else {
                //when bytes is null, just skip data.
                b.position(b.position() + read);
            }
            need -= read;
            offset += read;
            if (b.remaining() == 0) {
                ByteBuffer removed = mBuffers.remove();
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
                mBuffers.addFirst(b);
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
        if (remaining() == 0)
            return EMPTY_BYTEBUFFER;
        read(remaining());
        return remove();
    }

    private ByteBuffer read(int count) {
        if (remaining() < count)
            throw new IllegalArgumentException("count : " + remaining() + "/" + count);

        ByteBuffer first = mBuffers.peek();
        while (first != null && !first.hasRemaining()) {
            reclaim(mBuffers.remove());
            first = mBuffers.peek();
        }
        
        if (first == null) {
            return EMPTY_BYTEBUFFER;
        }

        if (first.remaining() >= count) {
            return first.order(order);
        }

        ByteBuffer ret = obtain(count);
        ret.limit(count);
        byte[] bytes = ret.array();
        int offset = 0;
        ByteBuffer bb = null;
        while (offset < count) {
            bb = mBuffers.remove();
            int toRead = Math.min(count - offset, bb.remaining());
            bb.get(bytes, offset, toRead);
            offset += toRead;
            if (bb.remaining() == 0) {
                reclaim(bb);
                bb = null;
            }
        }
        // if there was still data left in the last buffer we popped
        // toss it back into the head
        if (bb != null && bb.remaining() > 0)
            mBuffers.addFirst(bb);
        mBuffers.addFirst(ret);
        return ret.order(order);
    }
    
    public void trim() {
        // this clears out buffers that are empty in the beginning of the list
        read(0);
    }

    public ByteBufferList add(ByteBufferList b) {
        b.get(this);
        return this;
    }

    public ByteBufferList add(ByteBuffer b) {
        if (b.remaining() <= 0) {
//            System.out.println("reclaiming remaining: " + b.remaining());
            reclaim(b);
            return this;
        }
        addRemaining(b.remaining());
        // see if we can fit the entirety of the buffer into the end
        // of the current last buffer
        if (mBuffers.size() > 0) {
            ByteBuffer last = mBuffers.getLast();
            if (last.capacity() - last.limit() >= b.remaining()) {
                last.mark();
                last.position(last.limit());
                last.limit(last.capacity());
                last.put(b);
                last.limit(last.position());
                last.reset();
                reclaim(b);
                trim();
                return this;
            }
        }
        mBuffers.add(b);
        trim();
        return this;
    }

    public void addFirst(ByteBuffer b) {
        if (b.remaining() <= 0) {
            reclaim(b);
            return;
        }
        addRemaining(b.remaining());
        // see if we can fit the entirety of the buffer into the beginning
        // of the current first buffer
        if (mBuffers.size() > 0) {
            ByteBuffer first = mBuffers.getFirst();
            if (first.position() >= b.remaining()) {
                first.position(first.position() - b.remaining());
                first.mark();
                first.put(b);
                first.reset();
                reclaim(b);
                return;
            }
        }
        mBuffers.addFirst(b);
    }

    private void addRemaining(int remaining) {
        if (this.remaining() >= 0)
            this.remaining += remaining;
    }

    public void recycle() {
        while (mBuffers.size() > 0) {
            reclaim(mBuffers.remove());
        }
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

    public String peekString() {
        return peekString(null);
    }

    // not doing toString as this is really nasty in the debugger...
    public String peekString(Charset charset) {
        if (charset == null)
            charset = Charsets.UTF_8;
        StringBuilder builder = new StringBuilder();
        for (ByteBuffer bb: mBuffers) {
            byte[] bytes;
            int offset;
            int length;
            if (bb.isDirect()) {
                bytes = new byte[bb.remaining()];
                offset = 0;
                length = bb.remaining();
                bb.get(bytes);
            }
            else {
                bytes = bb.array();
                offset = bb.arrayOffset() + bb.position();
                length = bb.remaining();
            }
            builder.append(new String(bytes, offset, length, charset));
        }
        return builder.toString();
    }

    public String readString() {
        return readString(null);
    }

    public String readString(Charset charset) {
        String ret = peekString(charset);
        recycle();
        return ret;
    }

    static class Reclaimer implements Comparator<ByteBuffer> {
        @Override
        public int compare(ByteBuffer byteBuffer, ByteBuffer byteBuffer2) {
            // keep the smaller ones at the head, so they get tossed out quicker
            if (byteBuffer.capacity() == byteBuffer2.capacity())
                return 0;
            if (byteBuffer.capacity() > byteBuffer2.capacity())
                return 1;
            return -1;
        }
    }

    static PriorityQueue<ByteBuffer> reclaimed = new PriorityQueue<ByteBuffer>(8, new Reclaimer());

    private static PriorityQueue<ByteBuffer> getReclaimed() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper != null) {
            if (Thread.currentThread() == mainLooper.getThread())
                return null;
        }
        return reclaimed;
    }

    private static int MAX_SIZE = 1024 * 1024;
    public static int MAX_ITEM_SIZE = 1024 * 256;
    static int currentSize = 0;
    static int maxItem = 0;

    public static void setMaxPoolSize(int size) {
        MAX_SIZE = size;
    }

    public static void setMaxItemSize(int size) {
        MAX_ITEM_SIZE = size;
    }

    private static boolean reclaimedContains(ByteBuffer b) {
        for (ByteBuffer other: reclaimed) {
            if (other == b)
                return true;
        }
        return false;
    }

    public static void reclaim(ByteBuffer b) {
        if (b == null || b.isDirect())
            return;
        if (b.arrayOffset() != 0 || b.array().length != b.capacity())
            return;
        if (b.capacity() < 8192)
            return;
        if (b.capacity() > MAX_ITEM_SIZE)
            return;

        PriorityQueue<ByteBuffer> r = getReclaimed();
        if (r == null)
            return;

        synchronized (LOCK) {
            while (currentSize > MAX_SIZE && r.size() > 0 && r.peek().capacity() < b.capacity()) {
//                System.out.println("removing for better: " + b.capacity());
                ByteBuffer head = r.remove();
                currentSize -= head.capacity();
            }

            if (currentSize > MAX_SIZE) {
//                System.out.println("too full: " + b.capacity());
                return;
            }

            b.position(0);
            b.limit(b.capacity());
            currentSize += b.capacity();

            r.add(b);

            maxItem = Math.max(maxItem, b.capacity());
        }
    }

    private static final Object LOCK = new Object();

    public static ByteBuffer obtain(int size) {
        if (size <= maxItem) {
            PriorityQueue<ByteBuffer> r = getReclaimed();
            if (r != null) {
                synchronized (LOCK) {
                    while (r.size() > 0) {
                        ByteBuffer ret = r.remove();
                        if (r.size() == 0)
                            maxItem = 0;
                        currentSize -= ret.capacity();
                        if (ret.capacity() >= size) {
//                            System.out.println("using " + ret.capacity());
                            return ret;
                        }
//                        System.out.println("dumping " + ret.capacity());
                    }
                }
            }
        }

//        System.out.println("alloc for " + size);
        ByteBuffer ret = ByteBuffer.allocate(Math.max(8192, size));
        return ret;
    }

    public static void obtainArray(ByteBuffer[] arr, int size) {
        PriorityQueue<ByteBuffer> r = getReclaimed();
        int index = 0;
        int total = 0;

        if (r != null) {
            synchronized (LOCK) {
                while (r.size() > 0 && total < size && index < arr.length - 1) {
                    ByteBuffer b = r.remove();
                    currentSize -= b.capacity();
                    int needed = Math.min(size - total, b.capacity());
                    total += needed;
                    arr[index++] = b;
                }
            }
        }

        if (total < size) {
            ByteBuffer b = ByteBuffer.allocate(Math.max(8192, size - total));
            arr[index++] = b;
        }

        for (int i = index; i < arr.length; i++) {
            arr[i] = EMPTY_BYTEBUFFER;
        }
    }

    public static ByteBuffer deepCopy(ByteBuffer copyOf) {
        if (copyOf == null)
            return null;
        return (ByteBuffer)obtain(copyOf.remaining()).put(copyOf.duplicate()).flip();
    }

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);

    public static void writeOutputStream(OutputStream out, ByteBuffer b) throws IOException {
        byte[] bytes;
        int offset;
        int length;
        if (b.isDirect()) {
            bytes = new byte[b.remaining()];
            offset = 0;
            length = b.remaining();
            b.get(bytes);
        }
        else {
            bytes = b.array();
            offset = b.arrayOffset() + b.position();
            length = b.remaining();
        }
        out.write(bytes, offset, length);
    }
}
