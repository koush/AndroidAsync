package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.spdy.okio.Buffer;
import com.koushikdutta.async.http.spdy.okio.Segment;
import com.koushikdutta.async.http.spdy.okio.SegmentPool;
import com.koushikdutta.async.http.spdy.okio.Sink;
import com.koushikdutta.async.http.spdy.okio.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by koush on 7/25/14.
 */
public class ByteBufferListSink extends ByteBufferList implements Sink {
    @Override
    public void write(Buffer source, long byteCount) throws IOException {
        Segment s = source.head;
        while (byteCount > 0) {
            int toCopy = (int) Math.min(byteCount, s.limit - s.pos);
            ByteBuffer b = obtain(toCopy);
            b.put(s.data, s.pos, toCopy);
            b.flip();
            add(b);

            s.pos += toCopy;
            source.size -= toCopy;
            byteCount -= toCopy;

            if (s.pos == s.limit) {
                Segment toRecycle = s;
                source.head = s = toRecycle.pop();
                SegmentPool.getInstance().recycle(toRecycle);
            }
        }
    }

    @Override
    public void flush() throws IOException {
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
