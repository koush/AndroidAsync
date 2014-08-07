package com.koushikdutta.async.test;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.PushParser;
import com.koushikdutta.async.TapCallback;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.DataCallback;

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
            .until((byte)0, new DataCallback.NullDataCallback())
            .readInt(new PushParser.ParseCallback<Integer>() {
                public void parsed(Integer arg) {
                    valRead = arg;
                }
            });
        byte[] bytes = new byte[] { 5, 5, 5, 5, 0, 10, 5, 5, 5 };
        Util.emitAllData(mock, new ByteBufferList(bytes));
        assertEquals(valRead, 0x0A050505);
    }

    public void testPushParserTapUntil() {
        valRead = 0;
        FilteredDataEmitter mock = new FilteredDataEmitter() {
            @Override
            public boolean isPaused() {
                return false;
            }
        };
        new PushParser(mock)
                .until((byte)0, new DataCallback.NullDataCallback())
                .readInt()
                .tap(new TapCallback() {
                    public void parsed(int arg) {
                        valRead = arg;
                    }
                });
        byte[] bytes = new byte[] { 5, 5, 5, 5, 0, 10, 5, 5, 5 };
        Util.emitAllData(mock, new ByteBufferList(bytes));
        assertEquals(valRead, 0x0A050505);
    }

    int readInt;
    byte readByte;
    String readString;

    public void testTapCallback() {
        readInt = 0;
        readByte = 0;
        readString = "";

        FilteredDataEmitter mock = new FilteredDataEmitter() {
            @Override
            public boolean isPaused() {
                return false;
            }
        };
        new PushParser(mock)
                .readInt()
                .readByte()
                .readString()
                .tap(new TapCallback() {
                    void tap(int i, byte b, String s) {
                        readInt = i;
                        readByte = b;
                        readString = s;
                    }
                });

        byte[] bytes = new byte[] { 10, 5, 5, 5, 3, 0, 0, 0, 4, 116, 101, 115, 116 };
        Util.emitAllData(mock, new ByteBufferList(bytes));
        assertEquals(readInt, 0x0A050505);
        assertEquals(readByte, (byte) 3);
        assertEquals(readString, "test");
    }
}
