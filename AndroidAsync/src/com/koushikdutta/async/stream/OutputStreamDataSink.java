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

    AsyncServer server;
    public OutputStreamDataSink(AsyncServer server, OutputStream stream) {
        this.server = server;
        setOutputStream(stream);
    }

    OutputStream mStream;
    public void setOutputStream(OutputStream stream) {
        mStream = stream;
    }
    
    public OutputStream getOutputStream() throws IOException {
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
                getOutputStream().write(b.array(), b.arrayOffset() + b.position(), b.remaining());
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
    int totalWritten;

    @Override
    public void write(final ByteBuffer bb) {
        try {
            getOutputStream().write(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
        }
        catch (IOException e) {
            reportClose(e);
        }
        bb.position(0);
        bb.limit(0);
    }

    @Override
    public void write(final ByteBufferList bb) {
        try {
            while (bb.size() > 0) {
                ByteBuffer b = bb.remove();
                getOutputStream().write(b.array(), b.arrayOffset() + b.position(), b.remaining());
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
