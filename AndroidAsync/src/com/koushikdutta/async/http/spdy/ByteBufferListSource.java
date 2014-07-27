package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.spdy.okio.Buffer;
import com.koushikdutta.async.http.spdy.okio.Source;
import com.koushikdutta.async.http.spdy.okio.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by koush on 7/25/14.
 */
public class ByteBufferListSource extends ByteBufferList implements Source {
    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
        if (!hasRemaining())
            throw new AssertionError("empty!");
        int total = 0;
        while (total < byteCount && hasRemaining()) {
            ByteBuffer b = remove();
            int toRead = (int)Math.min(byteCount - total, b.remaining());
            total += toRead;
            sink.write(b.array(), b.arrayOffset() + b.position(), toRead);
            b.position(b.position() + toRead);
            addFirst(b);
        }
        return total;
    }

    @Override
    public Timeout timeout() {
        return Timeout.NONE;
    }

    @Override
    public void close() throws IOException {
        recycle();
    }
}
