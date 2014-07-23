package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.spdy.okio.Buffer;
import com.koushikdutta.async.http.spdy.okio.BufferedSource;
import com.koushikdutta.async.http.spdy.okio.ByteString;
import com.koushikdutta.async.http.spdy.okio.Sink;
import com.koushikdutta.async.http.spdy.okio.Timeout;
import com.koushikdutta.async.util.Charsets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Created by koush on 7/17/14.
 */
public class ByteBufferListSource extends ByteBufferList implements BufferedSource {
    @Override
    public Buffer buffer() {
        return null;
    }

    @Override
    public boolean exhausted() throws IOException {
        return !hasRemaining();
    }

    @Override
    public void require(long byteCount) throws IOException {
        if (remaining() < byteCount)
            throw new AssertionError("out of data");
    }

    @Override
    public byte readByte() throws IOException {
        return order(ByteOrder.BIG_ENDIAN).get();
    }

    @Override
    public short readShort() throws IOException {
        return order(ByteOrder.BIG_ENDIAN).getShort();
    }

    @Override
    public short readShortLe() throws IOException {
        return order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    @Override
    public int readInt() throws IOException {
        return order(ByteOrder.BIG_ENDIAN).getInt();
    }

    @Override
    public int readIntLe() throws IOException {
        return order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    @Override
    public long readLong() throws IOException {
        return order(ByteOrder.BIG_ENDIAN).getLong();
    }

    @Override
    public long readLongLe() throws IOException {
        return order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    @Override
    public void skip(long byteCount) throws IOException {
        if (byteCount > Integer.MAX_VALUE)
            throw new AssertionError("too much skippy, use less peanut butter");
        read(new byte[(int)byteCount]);
    }

    @Override
    public ByteString readByteString() throws IOException {
        return readByteString(remaining());
    }

    @Override
    public ByteString readByteString(long byteCount) throws IOException {
        return ByteString.of(readByteArray(byteCount));
    }

    @Override
    public byte[] readByteArray() throws IOException {
        return getAllByteArray();
    }

    @Override
    public byte[] readByteArray(long byteCount) throws IOException {
        byte[] ret = new byte[(int)byteCount];
        get(ret);
        return ret;
    }

    @Override
    public int read(byte[] sink) throws IOException {
        return read(sink, 0, sink.length);
    }

    @Override
    public void readFully(byte[] sink) throws IOException {
        read(sink, 0, sink.length);
    }

    @Override
    public int read(byte[] sink, int offset, int byteCount) throws IOException {
        get(sink, offset, byteCount);
        return byteCount;
    }

    @Override
    public void readFully(Buffer sink, long byteCount) throws IOException {
        throw new AssertionError("not implemented");
    }

    @Override
    public long readAll(Sink sink) throws IOException {
        throw new AssertionError("not implemented");
    }

    @Override
    public String readUtf8() throws IOException {
        return readUtf8(remaining());
    }

    @Override
    public String readUtf8(long byteCount) throws IOException {
        return new String(readByteArray(byteCount), Charsets.UTF_8);
    }

    @Override
    public String readUtf8Line() throws IOException {
        throw new AssertionError("not implemented");
    }

    @Override
    public String readUtf8LineStrict() throws IOException {
        throw new AssertionError("not implemented");
    }

    @Override
    public String readString(long byteCount, Charset charset) throws IOException {
        return new String(readByteArray(byteCount), charset);
    }

    @Override
    public long indexOf(byte b) throws IOException {
        throw new AssertionError("not implemented");
    }

    @Override
    public InputStream inputStream() {
        throw new AssertionError("not implemented");
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
        throw new AssertionError("not implemented");
    }

    @Override
    public Timeout timeout() {
        return null;
    }

    @Override
    public void close() throws IOException {
    }
}
