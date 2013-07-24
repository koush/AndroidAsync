package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;

public class PushParser {
    private LinkedList<Object> mWaiting = new LinkedList<Object>();

    static class BufferWaiter {
        int length;
    }
    
    static class StringWaiter extends BufferWaiter {
    }
    
    static class UntilWaiter {
        byte value;
        DataCallback callback;
    }
    
    int mNeeded = 0;
    public PushParser readInt() {
        mNeeded += 4;
        mWaiting.add(int.class);
        return this;
    }

    public PushParser readByte() {
        mNeeded += 1;
        mWaiting.add(byte.class);
        return this;
    }
    
    public PushParser readShort() {
        mNeeded += 2;
        mWaiting.add(short.class);
        return this;
    }
    
    public PushParser readLong() {
        mNeeded += 8;
        mWaiting.add(long.class);
        return this;
    }
    
    public PushParser readBuffer(int length) {
        if (length != -1)
            mNeeded += length;
        BufferWaiter bw = new BufferWaiter();
        bw.length = length;
        mWaiting.add(bw);
        return this;
    }

    public PushParser readLenBuffer() {
        readInt();
        BufferWaiter bw = new BufferWaiter();
        bw.length = -1;
        mWaiting.add(bw);
        return this;
    }
    
    public PushParser readString() {
        readInt();
        StringWaiter bw = new StringWaiter();
        bw.length = -1;
        mWaiting.add(bw);
        return this;
    }
    
    public PushParser until(byte b, DataCallback callback) {
        UntilWaiter waiter = new UntilWaiter();
        waiter.value = b;
        waiter.callback = callback;
        mWaiting.add(waiter);
        mNeeded++;
        return this;
    }
    
    public PushParser noop() {
        mWaiting.add(Object.class);
        return this;
    }

    DataEmitterReader mReader;
    DataEmitter mEmitter;
    public PushParser(DataEmitter s) {
        mEmitter = s;
        mReader = new DataEmitterReader();
        mEmitter.setDataCallback(mReader);
    }
    
    private ArrayList<Object> mArgs = new ArrayList<Object>();
    private TapCallback mCallback;
    
    Exception stack() {
        try {
            throw new Exception();
        }
        catch (Exception e) {
            return e;
        }
    }
    
    ByteOrder order = ByteOrder.BIG_ENDIAN;
    public ByteOrder order() {
        return order;
    }
    public PushParser order(ByteOrder order) {
        this.order = order;
        return this;
    }
    
    public void tap(TapCallback callback) {
        assert mCallback == null;
        assert mWaiting.size() > 0;

        mCallback = callback;
        
        new DataCallback() {
            {
                onDataAvailable(mEmitter, null);
            }
            
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                try {
                    if (bb != null)
                        bb.order(order);
                    while (mWaiting.size() > 0) {
                        Object waiting = mWaiting.peek();
                        if (waiting == null)
                            break;
//                        System.out.println("Remaining: " + bb.remaining());
                        if (waiting == int.class) {
                            mArgs.add(bb.getInt());
                            mNeeded -= 4;
                        }
                        else if (waiting == short.class) {
                            mArgs.add(bb.getShort());
                            mNeeded -= 2;
                        }
                        else if (waiting == byte.class) {
                            mArgs.add(bb.get());
                            mNeeded -= 1;
                        }
                        else if (waiting == long.class) {
                            mArgs.add(bb.getLong());
                            mNeeded -= 8;
                        }
                        else if (waiting == Object.class) {
                            mArgs.add(null);
                        }
                        else if (waiting instanceof UntilWaiter) {
                            UntilWaiter uw = (UntilWaiter)waiting;
                            boolean different = true;
                            ByteBufferList cb = new ByteBufferList();
                            while (bb.size() > 0) {
                                ByteBuffer b = bb.remove();
                                b.mark();
                                int index = 0;
                                while (b.remaining() > 0 && (different = (b.get() != uw.value))) {
                                    index++;
                                }
                                b.reset();
                                if (!different) {
                                    bb.addFirst(b);
                                    bb.get(cb, index);
                                    break;
                                }
                                else {
                                    cb.add(b);
                                }
                            }

                            if (uw.callback != null)
                                uw.callback.onDataAvailable(emitter, cb);

                            if (!different) {
                                mNeeded--;
                            }
                            else {
                                throw new Exception();
                            }
                        }
                        else if (waiting instanceof BufferWaiter || waiting instanceof StringWaiter) {
                            BufferWaiter bw = (BufferWaiter)waiting;
                            int length = bw.length;
                            if (length == -1) {
                                length = (Integer)mArgs.get(mArgs.size() - 1);
                                mArgs.remove(mArgs.size() - 1);
                                bw.length = length;
                                mNeeded += length;
                            }
                            if (bb.remaining() < length) {
//                                System.out.print("imminient feilure detected");
                                throw new Exception();
                            }
                            
//                            e.printStackTrace();
//                            System.out.println("Buffer length: " + length);
                            byte[] bytes = null;
                            if (length > 0) {
                                bytes = new byte[length];
                                bb.get(bytes);
                            }
                            mNeeded -= length;
                            if (waiting instanceof StringWaiter)
                                mArgs.add(new String(bytes));
                            else
                                mArgs.add(bytes);
                        }
                        else {
                            assert false;
                        }
//                        System.out.println("Parsed: " + mArgs.get(0));
                        mWaiting.remove();
                    }
                }
                catch (Exception ex) {
                    assert mNeeded != 0;
//                    ex.printStackTrace();
                    mReader.read(mNeeded, this);
                    return;
                }
                
                try {
                    Object[] args = mArgs.toArray();
                    mArgs.clear();
                    TapCallback callback = mCallback;
                    mCallback = null;
                    Method method = getTap(callback);
                    method.invoke(callback, args);
                }
                catch (Exception ex) {
                    assert false;
                    ex.printStackTrace();
                }
            }
        };
    }

    static Hashtable<Class, Method> mTable = new Hashtable<Class, Method>();
    static Method getTap(TapCallback callback) {
        Method found = mTable.get(callback.getClass());
        if (found != null)
            return found;
        // try the proguard friendly route, take the first/only method
        // in case "tap" has been renamed
        Method[] candidates = callback.getClass().getDeclaredMethods();
        if (candidates.length == 1)
            return candidates[0];

        for (Method method : callback.getClass().getMethods()) {
            if ("tap".equals(method.getName())) {
                mTable.put(callback.getClass(), method);
                return method;
            }
        }
        String fail =
        "-keep class * extends com.koushikdutta.async.TapCallback {\n" +
        "    *;\n" +
        "}\n";

        //null != "AndroidAsync: tap callback could not be found. Proguard? Use this in your proguard config:\n" + fail;
        assert false;
        return null;
    }
}
