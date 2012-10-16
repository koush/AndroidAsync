package com.koushikdutta.async.http.filter;

import java.nio.ByteBuffer;
import java.util.zip.Inflater;

import junit.framework.Assert;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataCallback;
import com.koushikdutta.async.Util;

public class InflaterInputFilter extends FilteredDataCallback {
    private Inflater mInflater;

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        try {
            ByteBufferList transformed = new ByteBufferList();
            ByteBuffer output = ByteBuffer.allocate(bb.remaining() * 2);
            int totalInflated = 0;
            int totalRead = 0;
            while (bb.size() > 0) {
                ByteBuffer b = bb.remove();
                if (b.hasRemaining()) {
                    totalRead =+ b.remaining();
                    mInflater.setInput(b.array(), b.arrayOffset() + b.position(), b.remaining());
                    do {
                        int inflated = mInflater.inflate(output.array(), output.arrayOffset() + output.position(), output.remaining());
                        totalInflated += inflated;
                        output.position(output.position() + inflated);
                        if (!output.hasRemaining()) {
                            output.limit(output.position());
                            output.position(0);
                            transformed.add(output);
                            Assert.assertNotSame(totalRead, 0);
                            int newSize = output.capacity() * 2;
                            output = ByteBuffer.allocate(newSize);
                        }
                    }
                    while (!mInflater.needsInput() && !mInflater.finished());
                }
            }
            output.limit(output.position());
            output.position(0);
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
