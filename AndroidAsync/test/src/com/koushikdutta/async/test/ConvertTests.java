package com.koushikdutta.async.test;

import android.support.test.runner.AndroidJUnit4;

import com.koushikdutta.async.future.SimpleFuture;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

import static com.koushikdutta.async.future.Converter.convert;

@RunWith(AndroidJUnit4.class)
public class ConvertTests {
    @Test
    public void testConvert() throws Exception {
        convert(new SimpleFuture<>(new JSONObject()))
        .to(ByteBuffer.class)
        .get();
    }
}
