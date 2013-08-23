package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.wrapper.AsyncSocketWrapper;
import com.koushikdutta.async.wrapper.DataEmitterWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class Util {
    public static void emitAllData(DataEmitter emitter, ByteBufferList list) {
        int remaining;
        DataCallback handler = null;
        while (!emitter.isPaused() && (handler = emitter.getDataCallback()) != null && (remaining = list.remaining()) > 0) {
            handler.onDataAvailable(emitter, list);
            if (remaining == list.remaining() && handler == emitter.getDataCallback()) {
                // not all the data was consumed...
                // call byteBufferList.recycle() or read all the data to prevent this assertion.
                // this is nice to have, as it identifies protocol or parsing errors.
                System.out.println("Data: " + list.peekString());
                System.out.println("handler: " + handler);
                assert false;
                throw new RuntimeException("mDataHandler failed to consume data, yet remains the mDataHandler.");
            }
        }
        if (list.remaining() != 0 && !emitter.isPaused()) {
            // not all the data was consumed...
            // call byteBufferList.recycle() or read all the data to prevent this assertion.
            // this is nice to have, as it identifies protocol or parsing errors.
            System.out.println("Data: " + list.peekString());
            System.out.println("handler: " + handler);
            assert false;
            throw new RuntimeException("mDataHandler failed to consume data, yet remains the mDataHandler.");
        }
    }

    public static void pump(final InputStream is, final DataSink ds, final CompletedCallback callback) {
        pump(is, Integer.MAX_VALUE, ds, callback);
    }

    public static void pump(final InputStream is, final int max, final DataSink ds, final CompletedCallback callback) {
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
                ByteBufferList.reclaim(pending);
                pending = null;
                try {
                    is.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            ByteBuffer pending;
            int mToAlloc = 0;
            int maxAlloc = 256 * 1024;

            @Override
            public void onWriteable() {
                try {
                    do {
                        if (pending == null || pending.remaining() == 0) {
                            ByteBufferList.reclaim(pending);
                            pending = ByteBufferList.obtain(Math.min(Math.max(mToAlloc, 2 << 11), maxAlloc));

                            int toRead = Math.min(max - totalRead, pending.capacity());
                            int read = is.read(pending.array(), 0, toRead);
                            if (read == -1 || totalRead == max) {
                                cleanup();
                                wrapper.onCompleted(null);
                                return;
                            }
                            mToAlloc = read * 2;
                            totalRead += read;
                            pending.position(0);
                            pending.limit(read);
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
        final ByteBufferList pending = new ByteBufferList();
        final DataCallback dataCallback = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                bb.get(pending);
                sink.write(pending);
                if (pending.remaining() > 0)
                    emitter.pause();
            }
        };
        emitter.setDataCallback(dataCallback);
        sink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                dataCallback.onDataAvailable(emitter, new ByteBufferList());
                emitter.resume();
            }
        });

        CompletedCallback wrapper = new CompletedCallback() {
            boolean reported;
            @Override
            public void onCompleted(Exception ex) {
                if (reported)
                    return;
                emitter.setEndCallback(null);
                sink.setClosedCallback(null);
                sink.setWriteableCallback(null);
                reported = true;
                callback.onCompleted(ex);
            }
        };

        emitter.setEndCallback(wrapper);
        sink.setClosedCallback(wrapper);
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
        ByteBuffer bb = ByteBuffer.wrap(bytes);
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
}
