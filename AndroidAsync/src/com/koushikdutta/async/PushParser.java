package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

public class PushParser implements DataCallback {

    static abstract class Waiter {
        int length;
        /**
         * Consumes received data, and/or returns next waiter to continue reading instead of this waiter.
         * @param bb received data, bb.remaining >= length
         * @return - a waiter that should continue reading right away, or null if this waiter is finished
         */
        public abstract Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb);
    }

    static class IntWaiter extends Waiter {
        TapCallback<Integer> callback;
        public IntWaiter(TapCallback<Integer> callback) {
            this.callback = callback;
            this.length = 4;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            callback.tap(bb.getInt());
            return null;
        }
    }

    static class BufferWaiter extends Waiter {
        TapCallback<byte[]> callback;
        public BufferWaiter(int length, TapCallback<byte[]> callback) {
            if (length <= 0) throw new IllegalArgumentException("length should be > 0");
            this.length = length;
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            byte[] bytes = new byte[length];
            bb.get(bytes);
            callback.tap(bytes);
            return null;
        }
    }

    static class UntilWaiter extends Waiter {

        byte value;
        DataCallback callback;
        public UntilWaiter(byte value, DataCallback callback) {
            this.length = 1;
            this.value = value;
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            boolean found = true;
            ByteBufferList cb = new ByteBufferList();
            while (bb.size() > 0) {
                ByteBuffer b = bb.remove();
                b.mark();
                int index = 0;
                while (b.remaining() > 0 && !(found = (b.get() == value))) {
                    index++;
                }
                b.reset();
                if (found) {
                    bb.addFirst(b);
                    bb.get(cb, index);
                    // eat the one we're waiting on
                    bb.get();
                    break;
                } else {
                    cb.add(b);
                }
            }

            callback.onDataAvailable(emitter, cb);

            if (found) {
                return null;
            } else {
                return this;
            }
        }

    }

    DataEmitter mEmitter;
    private LinkedList<Waiter> mWaiting = new LinkedList<Waiter>();
    ByteOrder order = ByteOrder.BIG_ENDIAN;

    public PushParser(DataEmitter s) {
        mEmitter = s;
        mEmitter.setDataCallback(this);
    }

    public PushParser readInt(TapCallback<Integer> callback) {
        mWaiting.add(new IntWaiter(callback));
        return this;
    }

    public PushParser readBuffer(int length, TapCallback<byte[]> callback) {
        mWaiting.add(new BufferWaiter(length, callback));
        return this;
    }

    public PushParser until(byte b, DataCallback callback) {
        mWaiting.add(new UntilWaiter(b, callback));
        return this;
    }

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {

        while (mWaiting.size() > 0 && bb.remaining() >= mWaiting.peek().length) {
            bb.order(order);
            Waiter next = mWaiting.poll().onDataAvailable(emitter, bb);
            if (next != null) mWaiting.addFirst(next);
        }
    }
}
