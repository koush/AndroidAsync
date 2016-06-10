package com.koushikdutta.async.test;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.util.Charsets;

import junit.framework.TestCase;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Semaphore;

/**
 * Created by koush on 6/9/16.
 */
public class LineEmitterTests extends TestCase {
    public void testFunnyCharacter() {
        final String stuff = "é\n";
        LineEmitter emitter = new LineEmitter(Charsets.UTF_8);
        emitter.setLineCallback(new LineEmitter.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                assertEquals(s + '\n', stuff);
            }
        });


        assertEquals(stuff.charAt(0), 233);
        ByteBufferList bb = new ByteBufferList(ByteBuffer.wrap(stuff.getBytes(Charsets.UTF_8)));
        emitter.onDataAvailable(null, bb);
    }
}
