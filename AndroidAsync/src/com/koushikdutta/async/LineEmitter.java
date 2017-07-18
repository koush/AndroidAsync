package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

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
        String s = bb.readString();
        if (s.indexOf("\n") > 0)
            mLineCallback.onStringAvailable(s);
    }
}
