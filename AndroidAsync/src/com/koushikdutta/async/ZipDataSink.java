package com.koushikdutta.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.koushikdutta.async.callback.CompletedCallback;

public class ZipDataSink extends FilteredDataSink implements CompletedEmitter {
    public ZipDataSink(DataSink sink) {
        super(sink);
    }

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ZipOutputStream zop = new ZipOutputStream(bout);
    boolean first = true;

    public void putNextEntry(ZipEntry ze) throws IOException {
        zop.putNextEntry(ze);
    }

    public void closeEntry() throws IOException {
        zop.closeEntry();
    }
    
    protected void report(Exception e) {
        if (mCompleted != null)
            mCompleted.onCompleted(null);
    }

    private boolean closed = false;
    public void close() {
        closed = true;
        try {
            zop.close();
        }
        catch (IOException e) {
            report(e);
            return;
        }
        write(new ByteBufferList());
    }

    
    @Override
    protected void onFlushed() {
        if (closed) {
            if (bout.size() > 0) {
                write(new ByteBufferList());
                return;
            }
            report(null);
        }
    }

    @Override
    public ByteBufferList filter(ByteBufferList bb) {
        try {
            if (bb != null) {
                for (ByteBuffer b: bb) {
                    zop.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
                }
            }
            ByteBufferList ret = new ByteBufferList(bout.toByteArray());
            bout.reset();
            bb.clear();
            return ret;
        }
        catch (IOException e) {
            report(e);
            return null;
        }
    }

    private CompletedCallback mCompleted;
    @Override
    public void setCompletedCallback(CompletedCallback callback) {
        mCompleted = callback;
    }

    @Override
    public CompletedCallback getCompletedCallback() {
        return mCompleted;
    }
}
