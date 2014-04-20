package com.koushikdutta.async.test;

import android.util.Log;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;

import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

/**
 * Created by koush on 4/20/14.
 */
public class ProxyTests extends TestCase {
    private void disabledTestSSLProxy() throws Exception {
        AsyncHttpGet get = new AsyncHttpGet("http://yahoo.com");
        get.enableProxy("192.168.2.21", 8888);
        get.setLogging("SSLProxy", Log.VERBOSE);
        String ret = AsyncHttpClient.getDefaultInstance().executeString(get, null).get(5000, TimeUnit.DAYS.MILLISECONDS);
    }
}
