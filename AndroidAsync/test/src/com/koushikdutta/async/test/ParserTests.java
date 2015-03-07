package com.koushikdutta.async.test;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.parser.DocumentParser;
import com.koushikdutta.async.parser.StringParser;
import com.koushikdutta.async.util.Charsets;

import junit.framework.TestCase;

import org.w3c.dom.Document;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Created by koush on 7/10/14.
 */
public class ParserTests extends TestCase {
    public void testString() throws Exception {
        StringParser p = new StringParser();
        FilteredDataEmitter f = new FilteredDataEmitter() {
            @Override
            public boolean isPaused() {
                return false;
            }
        };
        Future<String> ret = p.parse(f);
        ByteBufferList l = new ByteBufferList();
        l.add(ByteBuffer.wrap("foo".getBytes(Charsets.US_ASCII.name())));
        f.onDataAvailable(f, l);
        f.getEndCallback().onCompleted(null);
        String s = ret.get();
        assertEquals(s, "foo");
    }

    public void testUtf8String() throws Exception {
        StringParser p = new StringParser();
        FilteredDataEmitter f = new FilteredDataEmitter() {
            @Override
            public String charset() {
                return Charsets.UTF_8.name();
            }

            @Override
            public boolean isPaused() {
                return false;
            }
        };
        Future<String> ret = p.parse(f);
        ByteBufferList l = new ByteBufferList();
        l.add(ByteBuffer.wrap("æææ".getBytes(Charsets.UTF_8.name())));
        f.onDataAvailable(f, l);
        f.getEndCallback().onCompleted(null);
        String s = ret.get();
        assertEquals(s, "æææ");
    }

    public void testAsyncParserBase() throws Exception {
        DocumentParser parser = new DocumentParser();
        assertEquals(parser.getType(), Document.class);
    }
}
