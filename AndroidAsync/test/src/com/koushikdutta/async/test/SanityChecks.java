package com.koushikdutta.async.test;

import junit.framework.TestCase;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by koush on 5/15/13.
 */
public class SanityChecks extends TestCase {
    public void testByteOrder() {
        assertTrue(ByteBuffer.allocate(0).order().equals(ByteOrder.BIG_ENDIAN));
    }
}
