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
    public OutputStreamDataSink() {
    }

    @Override
    public void end() {
        close();
    }

    public OutputStreamDataSink(OutputStream stream) {
        setOutputStream(stream);
    }

    OutputStream mStream;
    public void setOutputStream(OutputStream stream) {
        mStream = stream;
    }
    
    public OutputStream getOutputStream() {
        return mStream;
    }
    
    @Override
    public void write(ByteBuffer bb) {
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
    public void write(ByteBufferList bb) {
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
    public void reportClose(Exception ex) {
        if (closeReported)
            return;
        closeReported = true;
        if (mClosedCallback != null)
            mClosedCallback.onCompleted(ex);
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
        return AsyncServer.getDefault();
    }
}
