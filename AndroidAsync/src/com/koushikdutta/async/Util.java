package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.util.Allocator;
import com.koushikdutta.async.util.StreamUtility;
import com.koushikdutta.async.wrapper.AsyncSocketWrapper;
import com.koushikdutta.async.wrapper.DataEmitterWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class Util {
    public static boolean SUPRESS_DEBUG_EXCEPTIONS = false;
    public static void emitAllData(DataEmitter emitter, ByteBufferList list) {
        int remaining;
        DataCallback handler = null;
        while (!emitter.isPaused() && (handler = emitter.getDataCallback()) != null && (remaining = list.remaining()) > 0) {
            handler.onDataAvailable(emitter, list);
            if (remaining == list.remaining() && handler == emitter.getDataCallback() && !emitter.isPaused()) {
                // this is generally indicative of failure...

                // 1) The data callback has not changed
                // 2) no data was consumed
                // 3) the data emitter was not paused

                // call byteBufferList.recycle() or read all the data to prevent this assertion.
                // this is nice to have, as it identifies protocol or parsing errors.

//                System.out.println("Data: " + list.peekString());
                System.out.println("handler: " + handler);
                list.recycle();
                if (SUPRESS_DEBUG_EXCEPTIONS)
                    return;
                throw new RuntimeException("mDataHandler failed to consume data, yet remains the mDataHandler.");
            }
        }
        if (list.remaining() != 0 && !emitter.isPaused()) {
            // not all the data was consumed...
            // call byteBufferList.recycle() or read all the data to prevent this assertion.
            // this is nice to have, as it identifies protocol or parsing errors.
//            System.out.println("Data: " + list.peekString());
            System.out.println("handler: " + handler);
            System.out.println("emitter: " + emitter);
            list.recycle();
            if (SUPRESS_DEBUG_EXCEPTIONS)
                return;
//            throw new AssertionError("Not all data was consumed by Util.emitAllData");
        }
    }

    public static void pump(final InputStream is, final DataSink ds, final CompletedCallback callback) {
        pump(is, Integer.MAX_VALUE, ds, callback);
    }

    public static void pump(final InputStream is, final long max, final DataSink ds, final CompletedCallback callback) {
        final CompletedCallback wrapper = new CompletedCallback() {
            boolean reported;
            @Override
            public void onCompleted(Exception ex) {
                if (reported)
                    return;
                reported = true;
                callback.onCompleted(ex);
            }
        };

        final WritableCallback cb = new WritableCallback() {
            int totalRead = 0;
            private void cleanup() {
                ds.setClosedCallback(null);
                ds.setWriteableCallback(null);
                pending.recycle();
                StreamUtility.closeQuietly(is);
            }
            ByteBufferList pending = new ByteBufferList();
            Allocator allocator = new Allocator().setMinAlloc((int)Math.min(2 << 19, max));

            @Override
            public void onWriteable() {
                try {
                    do {
                        if (!pending.hasRemaining()) {
                            ByteBuffer b = allocator.allocate();

                            long toRead = Math.min(max - totalRead, b.capacity());
                            int read = is.read(b.array(), 0, (int)toRead);
                            if (read == -1 || totalRead == max) {
                                cleanup();
                                wrapper.onCompleted(null);
                                return;
                            }
                            allocator.track(read);
                            totalRead += read;
                            b.position(0);
                            b.limit(read);
                            pending.add(b);
                        }
                        
                        ds.write(pending);
                    }
                    while (!pending.hasRemaining());
                }
                catch (Exception e) {
                    cleanup();
                    wrapper.onCompleted(e);
                }
            }
        };
        ds.setWriteableCallback(cb);

        ds.setClosedCallback(wrapper);
        
        cb.onWriteable();
    }
    
    public static void pump(final DataEmitter emitter, final DataSink sink, final CompletedCallback callback) {
        final DataCallback dataCallback = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                sink.write(bb);
                if (bb.remaining() > 0)
                    emitter.pause();
            }
        };
        emitter.setDataCallback(dataCallback);
        sink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                emitter.resume();
            }
        });

        final CompletedCallback wrapper = new CompletedCallback() {
            boolean reported;
            @Override
            public void onCompleted(Exception ex) {
                if (reported)
                    return;
                reported = true;
                emitter.setDataCallback(null);
                emitter.setEndCallback(null);
                sink.setClosedCallback(null);
                sink.setWriteableCallback(null);
                callback.onCompleted(ex);
            }
        };

        emitter.setEndCallback(wrapper);
        sink.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex == null)
                    ex = new IOException("sink was closed before emitter ended");
                wrapper.onCompleted(ex);
            }
        });
    }
    
    public static void stream(AsyncSocket s1, AsyncSocket s2, CompletedCallback callback) {
        pump(s1, s2, callback);
        pump(s2, s1, callback);
    }
    
    public static void pump(final File file, final DataSink ds, final CompletedCallback callback) {
        try {
            if (file == null || ds == null) {
                callback.onCompleted(null);
                return;
            }
            final InputStream is = new FileInputStream(file);
            pump(is, ds, new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        is.close();
                        callback.onCompleted(ex);
                    }
                    catch (IOException e) {
                        callback.onCompleted(e);
                    }
                }
            });
        }
        catch (Exception e) {
            callback.onCompleted(e);
        }
    }

    public static void writeAll(final DataSink sink, final ByteBufferList bb, final CompletedCallback callback) {
        WritableCallback wc;
        sink.setWriteableCallback(wc = new WritableCallback() {
            @Override
            public void onWriteable() {
                sink.write(bb);
                if (bb.remaining() == 0 && callback != null) {
                    sink.setWriteableCallback(null);
                    callback.onCompleted(null);
                }
            }
        });
        wc.onWriteable();
    }
    public static void writeAll(DataSink sink, byte[] bytes, CompletedCallback callback) {
        ByteBuffer bb = ByteBufferList.obtain(bytes.length);
        bb.put(bytes);
        bb.flip();
        ByteBufferList bbl = new ByteBufferList();
        bbl.add(bb);
        writeAll(sink, bbl, callback);
    }

    public static <T extends AsyncSocket> T getWrappedSocket(AsyncSocket socket, Class<T> wrappedClass) {
        if (wrappedClass.isInstance(socket))
            return (T)socket;
        while (socket instanceof AsyncSocketWrapper) {
            socket = ((AsyncSocketWrapper)socket).getSocket();
            if (wrappedClass.isInstance(socket))
                return (T)socket;
        }
        return null;
    }

    public static DataEmitter getWrappedDataEmitter(DataEmitter emitter, Class wrappedClass) {
        if (wrappedClass.isInstance(emitter))
            return emitter;
        while (emitter instanceof DataEmitterWrapper) {
            emitter = ((AsyncSocketWrapper)emitter).getSocket();
            if (wrappedClass.isInstance(emitter))
                return emitter;
        }
        return null;
    }

    public static void end(DataEmitter emitter, Exception e) {
        if (emitter == null)
            return;
        end(emitter.getEndCallback(), e);
    }

    public static void end(CompletedCallback end, Exception e) {
        if (end != null)
            end.onCompleted(e);
    }

    public static void writable(DataSink emitter) {
        if (emitter == null)
            return;
        writable(emitter.getWriteableCallback());
    }

    public static void writable(WritableCallback writable) {
        if (writable != null)
            writable.onWriteable();
    }
}
