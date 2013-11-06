package com.koushikdutta.async.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;

public class OutputStreamDataSink implements DataSink {
    public OutputStreamDataSink(AsyncServer server) {
        this(server, null);
    }

    @Override
    public void end() {
        close();
    }

    public OutputStreamDataSink(AsyncServer server, OutputStream stream) {
        this(server, stream, false);
    }

    AsyncServer server;
    boolean blocking;
    public OutputStreamDataSink(AsyncServer server, OutputStream stream, boolean blocking) {
        this.server = server;
        this.blocking = blocking;
        setOutputStream(stream);
    }

    OutputStream mStream;
    public void setOutputStream(OutputStream stream) {
        mStream = stream;
    }
    
    public OutputStream getOutputStream() {
        return mStream;
    }

    private boolean doPending() {
        try {
            while (pending.size() > 0) {
                ByteBuffer b;
                synchronized (pending) {
                    b = pending.remove();
                }
                int rem = b.remaining();
                mStream.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
                totalWritten += rem;
                ByteBufferList.reclaim(b);
            }
            return true;
        }
        catch (Exception e) {
            pending.recycle();
            closeReported = true;
            closeException = e;
            return false;
        }
    }

    final ByteBufferList pending = new ByteBufferList();
    Runnable backgrounder;
    int totalWritten;
    private void doBackground() {
        assert getServer().getAffinity() == Thread.currentThread();

        if (backgrounder != null)
            return;
        backgrounder = new Runnable() {
            @Override
            public void run() {
                if (!doPending())
                    return;

                // once we're done, post back and try to do some more data.
                getServer().post(new Runnable() {
                    @Override
                    public void run() {
                        backgrounder = null;
                        if (outputStreamCallback != null && !pending.hasRemaining())
                            outputStreamCallback.onWriteable();

                        if (closeReported && !pending.hasRemaining()) {
                            if (mClosedCallback != null)
                                mClosedCallback.onCompleted(closeException);
                            return;
                        }

                        doBackground();
                    }
                });
            }
        };
        getServer().getExecutorService().execute(backgrounder);
    }

    @Override
    public void write(final ByteBuffer bb) {
        if (getServer().getAffinity() != Thread.currentThread() && blocking) {
            getServer().run(new Runnable() {
                @Override
                public void run() {
                    write(bb);
                }
            });
            return;
        }

        if (blocking) {
            // tune this number.
            // doesn't need to be locked.
            // background thread posts back on completion to recheck and retrigger.
            if (pending.remaining() > 256 * 1024)
                return;
            ByteBuffer dup = ByteBufferList.obtain(bb.remaining());
            dup.put(bb);
            dup.flip();
            synchronized (pending) {
                pending.add(dup);
            }
            doBackground();
            return;
        }

        try {
            mStream.write(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
        }
        catch (IOException e) {
            reportClose(e);
        }
        bb.position(0);
        bb.limit(0);
    }

    @Override
    public void write(final ByteBufferList bb) {
        if (getServer().getAffinity() != Thread.currentThread() && blocking) {
            getServer().run(new Runnable() {
                @Override
                public void run() {
                    write(bb);
                }
            });
            return;
        }

        if (blocking) {
            // tune this number.
            // doesn't need to be locked.
            // background thread posts back on completion to recheck and retrigger.
            if (pending.remaining() > 256 * 1024)
                return;
            synchronized (pending) {
                bb.get(pending);
            }
            doBackground();
            return;
        }

        try {
            while (bb.size() > 0) {
                ByteBuffer b = bb.remove();
                mStream.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
                ByteBufferList.reclaim(b);
            }
        }
        catch (IOException e) {
            reportClose(e);
        }
        finally {
            bb.recycle();
        }
    }

    WritableCallback mWritable;
    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mWritable = handler;        
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mWritable;
    }

    @Override
    public boolean isOpen() {
        return closeReported;
    }
    
    @Override
    public void close() {
        try {
            if (mStream != null)
                mStream.close();
            reportClose(null);
        }
        catch (IOException e) {
            reportClose(e);
        }
    }

    boolean closeReported;
    Exception closeException;
    public void reportClose(Exception ex) {
        if (closeReported)
            return;
        closeReported = true;
        closeException = ex;
        if (blocking) {
            getServer().post(new Runnable() {
                @Override
                public void run() {
                    doBackground();
                }
            });
            return;
        }

        if (mClosedCallback != null)
            mClosedCallback.onCompleted(closeException);
    }
    
    CompletedCallback mClosedCallback;
    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mClosedCallback = handler;        
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mClosedCallback;
    }

    @Override
    public AsyncServer getServer() {
        return server;
    }

    WritableCallback outputStreamCallback;
    public void setOutputStreamWritableCallback(WritableCallback outputStreamCallback) {
        this.outputStreamCallback = outputStreamCallback;
    }
}
