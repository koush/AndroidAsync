package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Inflater;

public class InflaterInputFilter extends FilteredDataEmitter {
    private Inflater mInflater;

    @Override
    protected void report(Exception e) {
        mInflater.end();
        if (e != null && mInflater.getRemaining() > 0) {
            e = new DataRemainingException("data still remaining in inflater", e);
        }
        super.report(e);
    }

    ByteBufferList transformed = new ByteBufferList();
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        try {
            ByteBuffer output = ByteBufferList.obtain(bb.remaining() * 2);
            int totalRead = 0;
            while (bb.size() > 0) {
                ByteBuffer b = bb.remove();
                if (b.hasRemaining()) {
                    totalRead =+ b.remaining();
                    mInflater.setInput(b.array(), b.arrayOffset() + b.position(), b.remaining());
                    do {
                        int inflated = mInflater.inflate(output.array(), output.arrayOffset() + output.position(), output.remaining());
                        output.position(output.position() + inflated);
                        if (!output.hasRemaining()) {
                            output.flip();
                            transformed.add(output);
                            int newSize = output.capacity() * 2;
                            output = ByteBufferList.obtain(newSize);
                        }
                    }
                    while (!mInflater.needsInput() && !mInflater.finished());
                }
                ByteBufferList.reclaim(b);
            }
            output.flip();
            transformed.add(output);

            Util.emitAllData(this, transformed);
        }
        catch (Exception ex) {
            report(ex);
        }
    }

    public InflaterInputFilter() {
        this(new Inflater());
    }

    public InflaterInputFilter(Inflater inflater) {
        mInflater = inflater;
    }
}
