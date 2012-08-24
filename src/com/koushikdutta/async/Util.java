package com.koushikdutta.async;

import junit.framework.Assert;

import com.koushikdutta.async.callback.DataCallback;

public class Util {
    public static void emitAllData(DataEmitter emitter, ByteBufferList list) {
        int remaining;
        while (emitter.getDataCallback() != null && (remaining = list.remaining()) > 0) {
            DataCallback handler = emitter.getDataCallback();
            handler.onDataAvailable(emitter, list);
            if (remaining == list.remaining() && handler == emitter.getDataCallback()) {
                Assert.fail("mDataHandler failed to consume data, yet remains the mDataHandler.");
                break;
            }
        }
        Assert.assertEquals(list.remaining(), 0);
    }
}
