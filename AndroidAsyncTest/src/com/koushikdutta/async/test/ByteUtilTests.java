package com.koushikdutta.async.test;

import com.koushikdutta.async.*;
import junit.framework.TestCase;

import java.nio.ByteBuffer;

/**
 * Created by koush on 5/17/13.
 */
public class ByteUtilTests extends TestCase {
    int valRead;
    public void testPushParserUntil() {
        valRead = 0;
        FilteredDataEmitter mock = new FilteredDataEmitter() {
            @Override
            public boolean isPaused() {
                return false;
            }
        };
        PushParser p = new PushParser(mock);
        p
            .until((byte)0, new NullDataCallback())
            .readInt()
            .tap(new TapCallback() {
                public void tap(int arg) {
                    valRead = arg;
                }
            });
        byte[] bytes = new byte[] { 5, 5, 5, 5, 0, 10, 5, 5, 5 };
        Util.emitAllData(mock, new ByteBufferList(bytes));
        assertEquals(valRead, 0x0A050505);
    }
}
