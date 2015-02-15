package com.koushikdutta.async;

import android.util.Log;
import com.koushikdutta.async.callback.DataCallback;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;

public class PushParser implements DataCallback {

    public interface ParseCallback<T> {
        public void parsed(T data);
    }

    static abstract class Waiter {
        int length;
        public Waiter(int length) {
            this.length = length;
        }
        /**
         * Consumes received data, and/or returns next waiter to continue reading instead of this waiter.
         * @param bb received data, bb.remaining >= length
         * @return - a waiter that should continue reading right away, or null if this waiter is finished
         */
        public abstract Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb);
    }

    static class IntWaiter extends Waiter {
        ParseCallback<Integer> callback;
        public IntWaiter(ParseCallback<Integer> callback) {
            super(4);
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            callback.parsed(bb.getInt());
            return null;
        }
    }

    static class ByteArrayWaiter extends Waiter {
        ParseCallback<byte[]> callback;
        public ByteArrayWaiter(int length, ParseCallback<byte[]> callback) {
            super(length);
            if (length <= 0)
                throw new IllegalArgumentException("length should be > 0");
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            byte[] bytes = new byte[length];
            bb.get(bytes);
            callback.parsed(bytes);
            return null;
        }
    }

    static class LenByteArrayWaiter extends Waiter {
        private final ParseCallback<byte[]> callback;

        public LenByteArrayWaiter(ParseCallback<byte[]> callback) {
            super(4);
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            int length = bb.getInt();
            if (length == 0) {
                callback.parsed(new byte[0]);
                return null;
            }
            return new ByteArrayWaiter(length, callback);
        }
    }


    static class ByteBufferListWaiter extends Waiter {
        ParseCallback<ByteBufferList> callback;
        public ByteBufferListWaiter(int length, ParseCallback<ByteBufferList> callback) {
            super(length);
            if (length <= 0) throw new IllegalArgumentException("length should be > 0");
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            callback.parsed(bb.get(length));
            return null;
        }
    }

    static class LenByteBufferListWaiter extends Waiter {
        private final ParseCallback<ByteBufferList> callback;

        public LenByteBufferListWaiter(ParseCallback<ByteBufferList> callback) {
            super(4);
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            int length = bb.getInt();
            return new ByteBufferListWaiter(length, callback);
        }
    }

    static class UntilWaiter extends Waiter {

        byte value;
        DataCallback callback;
        public UntilWaiter(byte value, DataCallback callback) {
            super(1);
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

    private class TapWaiter extends Waiter {
        private final TapCallback callback;

        public TapWaiter(TapCallback callback) {
            super(0);
            this.callback = callback;
        }

        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            Method method = getTap(callback);
            method.setAccessible(true);
            try {
                method.invoke(callback, args.toArray());
            } catch (Exception e) {
                Log.e("PushParser", "Error while invoking tap callback", e);
            }
            args.clear();
            return null;
        }
    }

    private Waiter noopArgWaiter = new Waiter(0) {
        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            args.add(null);
            return null;
        }
    };

    private Waiter byteArgWaiter = new Waiter(1) {
        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            args.add(bb.get());
            return null;
        }
    };

    private Waiter shortArgWaiter = new Waiter(2) {
        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            args.add(bb.getShort());
            return null;
        }
    };

    private Waiter intArgWaiter = new Waiter(4) {
        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            args.add(bb.getInt());
            return null;
        }
    };

    private Waiter longArgWaiter = new Waiter(8) {
        @Override
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            args.add(bb.getLong());
            return null;
        }
    };

    private ParseCallback<byte[]> byteArrayArgCallback = new ParseCallback<byte[]>() {
        @Override
        public void parsed(byte[] data) {
            args.add(data);
        }
    };

    private ParseCallback<ByteBufferList> byteBufferListArgCallback = new ParseCallback<ByteBufferList>() {
        @Override
        public void parsed(ByteBufferList data) {
            args.add(data);
        }
    };

    private ParseCallback<byte[]> stringArgCallback = new ParseCallback<byte[]>() {
        @Override
        public void parsed(byte[] data) {
            args.add(new String(data));
        }
    };

    DataEmitter mEmitter;
    private LinkedList<Waiter> mWaiting = new LinkedList<Waiter>();
    private ArrayList<Object> args = new ArrayList<Object>();
    ByteOrder order = ByteOrder.BIG_ENDIAN;

    public PushParser setOrder(ByteOrder order) {
        this.order = order;
        return this;
    }

    public PushParser(DataEmitter s) {
        mEmitter = s;
        mEmitter.setDataCallback(this);
    }

    public PushParser readInt(ParseCallback<Integer> callback) {
        mWaiting.add(new IntWaiter(callback));
        return this;
    }

    public PushParser readByteArray(int length, ParseCallback<byte[]> callback) {
        mWaiting.add(new ByteArrayWaiter(length, callback));
        return this;
    }

    public PushParser readByteBufferList(int length, ParseCallback<ByteBufferList> callback) {
        mWaiting.add(new ByteBufferListWaiter(length, callback));
        return this;
    }

    public PushParser until(byte b, DataCallback callback) {
        mWaiting.add(new UntilWaiter(b, callback));
        return this;
    }

    public PushParser readByte() {
        mWaiting.add(byteArgWaiter);
        return this;
    }

    public PushParser readShort() {
        mWaiting.add(shortArgWaiter);
        return this;
    }

    public PushParser readInt() {
        mWaiting.add(intArgWaiter);
        return this;
    }

    public PushParser readLong() {
        mWaiting.add(longArgWaiter);
        return this;
    }

    public PushParser readByteArray(int length) {
        return (length == -1) ? readLenByteArray() : readByteArray(length, byteArrayArgCallback);
    }

    public PushParser readLenByteArray() {
        mWaiting.add(new LenByteArrayWaiter(byteArrayArgCallback));
        return this;
    }

    public PushParser readByteBufferList(int length) {
        return (length == -1) ? readLenByteBufferList() : readByteBufferList(length, byteBufferListArgCallback);
    }

    public PushParser readLenByteBufferList() {
        return readLenByteBufferList(byteBufferListArgCallback);
    }

    public PushParser readLenByteBufferList(ParseCallback<ByteBufferList> callback) {
        mWaiting.add(new LenByteBufferListWaiter(callback));
        return this;
    }

    public PushParser readString() {
        mWaiting.add(new LenByteArrayWaiter(stringArgCallback));
        return this;
    }

    public PushParser noop() {
        mWaiting.add(noopArgWaiter);
        return this;
    }

    ByteBufferList pending = new ByteBufferList();
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        bb.get(pending);
        while (mWaiting.size() > 0 && pending.remaining() >= mWaiting.peek().length) {
            pending.order(order);
            Waiter next = mWaiting.poll().onDataAvailable(emitter, pending);
            if (next != null) mWaiting.addFirst(next);
        }
        if (mWaiting.size() == 0)
            pending.get(bb);
    }

    public void tap(TapCallback callback) {
        mWaiting.add(new TapWaiter(callback));
    }

    static Hashtable<Class, Method> mTable = new Hashtable<Class, Method>();
    static Method getTap(TapCallback callback) {
        Method found = mTable.get(callback.getClass());
        if (found != null)
            return found;

        for (Method method : callback.getClass().getMethods()) {
            if ("tap".equals(method.getName())) {
                mTable.put(callback.getClass(), method);
                return method;
            }
        }

        // try the proguard friendly route, take the first/only method
        // in case "tap" has been renamed
        Method[] candidates = callback.getClass().getDeclaredMethods();
        if (candidates.length == 1)
            return candidates[0];

        String fail =
            "-keep class * extends com.koushikdutta.async.TapCallback {\n" +
                    "    *;\n" +
                    "}\n";

        //null != "AndroidAsync: tap callback could not be found. Proguard? Use this in your proguard config:\n" + fail;
        throw new AssertionError(fail);
    }
}
