package com.koushikdutta.async.http.filter;

import java.nio.ByteBuffer;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.FilteredDataSink;

public class ChunkedOutputFilter extends FilteredDataSink {
    public ChunkedOutputFilter(DataSink sink) {
        super(sink);
    }

    @Override
    public ByteBufferList filter(ByteBufferList bb) {
        String chunkLen = Integer.toString(bb.remaining(), 16) + "\r\n";
        bb.addFirst(ByteBuffer.wrap(chunkLen.getBytes()));
        bb.add(ByteBuffer.wrap("\r\n".getBytes()));
        return bb;
    }
}
