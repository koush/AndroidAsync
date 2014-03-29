package com.koushikdutta.async.test;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.PushParser;
import com.koushikdutta.async.TapCallback;
import com.koushikdutta.async.Util;

import junit.framework.TestCase;

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
        new PushParser(mock)
            .until((byte)0, new NullDataCallback())
            .readInt(new TapCallback<Integer>() {
                public void tap(Integer arg) {
                    valRead = arg;
                }
            });
        byte[] bytes = new byte[] { 5, 5, 5, 5, 0, 10, 5, 5, 5 };
        Util.emitAllData(mock, new ByteBufferList(bytes));
        assertEquals(valRead, 0x0A050505);
    }
}
