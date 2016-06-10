package com.koushikdutta.async;

import android.util.Log;

import com.koushikdutta.async.callback.DataCallback;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class LineEmitter implements DataCallback {
    public interface StringCallback {
        void onStringAvailable(String s);
    }

    public LineEmitter() {
        this(null);
    }

    public LineEmitter(Charset charset) {
        this.charset = charset;
    }

    Charset charset;

    ByteBufferList data = new ByteBufferList();

    StringCallback mLineCallback;
    public void setLineCallback(StringCallback callback) {
        mLineCallback = callback;
    }

    public StringCallback getLineCallback() {
        return mLineCallback;
    }

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        ByteBuffer buffer = ByteBuffer.allocate(bb.remaining());
        while (bb.remaining() > 0) {
            byte b = bb.get();
            if (b == '\n') {
                assert mLineCallback != null;
                buffer.flip();
                data.add(buffer);
                mLineCallback.onStringAvailable(data.readString(charset));
                data = new ByteBufferList();
                return;
            }
            else {
                buffer.put(b);
            }
        }
        buffer.flip();
        data.add(buffer);
    }
}
