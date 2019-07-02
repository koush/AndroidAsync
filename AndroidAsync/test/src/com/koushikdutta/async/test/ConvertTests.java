package com.koushikdutta.async.test;

import androidx.test.runner.AndroidJUnit4;

import com.koushikdutta.async.future.SimpleFuture;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

import static com.koushikdutta.async.future.Converter.convert;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class ConvertTests {
    @Test
    public void testConvert() throws Exception {
        ByteBuffer buf = convert(new SimpleFuture<>(new JSONObject()))
        .to(ByteBuffer.class)
        .get();

        assertEquals(buf.remaining(), 2);
    }
}
