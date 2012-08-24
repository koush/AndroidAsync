package com.koushikdutta.async;

import junit.framework.Assert;

import com.koushikdutta.async.callback.DataCallback;

public class LineEmitter {
    static public interface StringCallback {
        public void onStringAvailable(String s);
    }

    StringBuilder data = new StringBuilder();

    public LineEmitter(final DataEmitter emitter) {
        emitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                while (bb.remaining() > 0) {
                    byte b = bb.get();
                    if (b == '\n') {
                        Assert.assertNotNull(mLineCallback);
                        mLineCallback.onStringAvailable(data.toString());
                        if (emitter.getDataCallback() != this)
                            return;
                        data = new StringBuilder();
                    }
                    else {
                        data.append((char)b);
                    }
                }
            }
        });
    }
    
    StringCallback mLineCallback;
    public void setLineCallback(StringCallback callback) {
        mLineCallback = callback;
    }

    public StringCallback getLineCallback() {
        return mLineCallback;
    }
}
