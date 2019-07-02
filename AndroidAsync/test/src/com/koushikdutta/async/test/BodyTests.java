package com.koushikdutta.async.test;

import androidx.test.runner.AndroidJUnit4;

import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by koush on 3/19/14.
 */
@RunWith(AndroidJUnit4.class)
public class BodyTests {
    @Test
    public void testNullValue() throws Exception {
        Multimap mm = new Multimap();
        mm.add("hello", null);
        UrlEncodedFormBody body = new UrlEncodedFormBody(mm);

        int length = body.length();
    }
}
