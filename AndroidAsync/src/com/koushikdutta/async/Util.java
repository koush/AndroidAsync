package com.koushikdutta.async;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;

public class Util {
    public static void emitAllData(DataEmitter emitter, ByteBufferList list) {
        int remaining;
        DataCallback handler = null;
        while (!emitter.isPaused() && (handler = emitter.getDataCallback()) != null && (remaining = list.remaining()) > 0) {
            handler.onDataAvailable(emitter, list);
            if (remaining == list.remaining() && handler == emitter.getDataCallback()) {
                Assert.fail("mDataHandler failed to consume data, yet remains the mDataHandler.");
                break;
            }
        }
        if (list.remaining() != 0 && !emitter.isPaused()) {
            System.out.println("Data: " + list.peekString());
            System.out.println("handler: " + handler);
            Assert.fail();
        }
    }

    public static void emitAllData(DataEmitter emitter, ByteBuffer b) {
        ByteBufferList list = new ByteBufferList();
        list.add(b);
        emitAllData(emitter, list);
        // previous call makes sure list is empty,
        // so this is safe to clear
        b.position(b.limit());
    }

    public static void pump(final InputStream is, final DataSink ds, final CompletedCallback callback) {
        final WritableCallback cb = new WritableCallback() {
            private void close() {
                try {
                    is.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            byte[] buffer = new byte[8192];
            ByteBuffer pending = ByteBuffer.wrap(buffer);
            {
                pending.limit(pending.position());
            }

            @Override
            public void onWriteable() {
                try {
                    int remaining;
//                    long start = System.currentTimeMillis();
                    do {
                        if (pending.remaining() == 0) {
//                            if (System.currentTimeMillis() - start > 1000) {
//                                ds.getServer().postDelayed(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        onWriteable();
//                                    } 
//                                }, 100);
//                                return;
//                            }
//                            int available = is.available();
//                            System.out.println(available);
                            int read = is.read(buffer);
                            if (read == -1) {
                                close();
                                callback.onCompleted(null);
                                return;
                            }
                            pending.position(0);
                            pending.limit(read);
                        }
                        
                        remaining = pending.remaining();
                        ds.write(pending);
                    }
                    while (remaining != pending.remaining());
                }
                catch (Exception e) {
                    close();
                    callback.onCompleted(e);
                    return;
                }
            }
        };
        ds.setWriteableCallback(cb);

        ds.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
//                Assert.fail();
                callback.onCompleted(ex);
            }
        });
        
        cb.onWriteable();
    }
    
    public static void pump(final DataEmitter emitter, final DataSink sink, final CompletedCallback callback) {
        emitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                sink.write(bb);
                if (bb.remaining() > 0)
                    emitter.pause();
            }
        });
        sink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                emitter.resume();
            }
        });
        
        emitter.setEndCallback(callback);
        sink.setClosedCallback(callback);
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
        sink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                if (bb.remaining() == 0)
                    return;
                sink.write(bb);
                if (bb.remaining() == 0 && callback != null)
                    callback.onCompleted(null);
            }
        });
        sink.write(bb);
        if (bb.remaining() == 0 && callback != null)
            callback.onCompleted(null);
    }
    public static void writeAll(DataSink sink, byte[] bytes, CompletedCallback callback) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        ByteBufferList bbl = new ByteBufferList();
        bbl.add(bb);
        writeAll(sink, bbl, callback);
    }
}
