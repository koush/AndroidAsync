package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

public class LineEmitter implements DataCallback {
    static public interface StringCallback {
        public void onStringAvailable(String s);
    }

    StringBuilder data = new StringBuilder();

    StringCallback mLineCallback;
    public void setLineCallback(StringCallback callback) {
        mLineCallback = callback;
    }

    public StringCallback getLineCallback() {
        return mLineCallback;
    }

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        while (bb.remaining() > 0) {
            byte b = bb.get();
            if (b == '\n') {
                assert mLineCallback != null;
                mLineCallback.onStringAvailable(data.toString());
                data = new StringBuilder();
                return;
            }
            else {
                data.append((char)b);
            }
        }        
    }
}
