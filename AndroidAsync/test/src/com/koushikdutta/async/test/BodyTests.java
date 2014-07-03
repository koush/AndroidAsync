package com.koushikdutta.async.test;

import android.test.AndroidTestCase;

import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;

/**
 * Created by koush on 3/19/14.
 */
public class BodyTests extends AndroidTestCase {
    public void testNullValue() throws Exception {
        Multimap mm = new Multimap();
        mm.add("hello", null);
        UrlEncodedFormBody body = new UrlEncodedFormBody(mm);

        int length = body.length();
    }
}
