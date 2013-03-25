package com.koushikdutta.async.test;

import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;

public class GoogleTest extends TestCase {
    public void testHomepage() throws Exception {
        AsyncHttpClient client = AsyncHttpClient.getDefaultInstance();
        
        Future<String> ret = client.get("http://google.com", (StringCallback)null);
        assertNotNull(ret.get(5000, TimeUnit.MILLISECONDS));
    }
    
    public void testGithubRandomData() {
        String dataNameAndHash = "6691924d7d24237d3b3679310157d640";
    }
}
