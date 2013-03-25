package com.koushikdutta.async.test;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import junit.framework.TestCase;
import android.os.Environment;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.DownloadCallback;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.HttpConnectCallback;
import com.koushikdutta.async.http.ResponseCacheMiddleware;

public class HttpClientTests extends TestCase {
    AsyncHttpClient client;
    AsyncServer server = new AsyncServer();
    
    public HttpClientTests() {
        super();
        server.setAutostart(true);
        client = new AsyncHttpClient(server);
    }
    
    private static final long TIMEOUT = 10000L; 
    public void testHomepage() throws Exception {
        Future<String> ret = client.get("http://google.com", (StringCallback)null);
        assertNotNull(ret.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }
    
    // this testdata file was generated using /dev/random. filename is also the md5 of the file.
    final static String dataNameAndHash = "6691924d7d24237d3b3679310157d640";
    final static String githubPath = "github.com/koush/AndroidAsync/raw/master/AndroidAsyncTest/testdata/";
    final static String github = "https://" + githubPath + dataNameAndHash;
    final static String githubInsecure = "http://" + githubPath + dataNameAndHash;
    public void testGithubRandomData() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        client.execute(github, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                // make sure gzip decoding works, as that is generally what github sends.
                Assert.assertEquals("gzip", response.getHeaders().getContentEncoding());
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        md5.update(bb);
                    }
                });
                
                response.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        semaphore.release();
                    }
                });
            }
        });
        
        assertTrue(semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(md5.digest().equals(dataNameAndHash));
    }
    
    public void testGithubRandomDataWithFuture() throws Exception {
        final Md5 md5 = Md5.createInstance();
        Future<ByteBufferList> bb = client.get(github, (DownloadCallback)null);
        md5.update(bb.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(md5.digest().equals(dataNameAndHash));
    }

    public void testInsecureGithubRandomDataWithFuture() throws Exception {
        final Md5 md5 = Md5.createInstance();
        Future<ByteBufferList> bb = client.get(githubInsecure, (DownloadCallback)null);
        md5.update(bb.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(md5.digest().equals(dataNameAndHash));
    }
    
    public void testGithubHelloWithFuture() throws Exception {
        Future<String> string = client.get("https://" + githubPath + "hello.txt", (StringCallback)null);
        assertEquals(string.get(TIMEOUT, TimeUnit.MILLISECONDS), "hello world");
    }
    
    public void testCache() throws Exception {
        ResponseCacheMiddleware cache = ResponseCacheMiddleware.addCache(client, new File(Environment.getExternalStorageDirectory(), "AndroidAsyncTest"), 1024 * 1024 * 10);
        try {
            // clear the old cache
            cache.clear();
            // populate the cache
            testGithubRandomData();
            // this should result in a conditional cache hit
            testGithubRandomData();
            assertEquals(cache.getConditionalCacheHitCount(), 1);
        }
        finally {
            client.getMiddleware().remove(cache);
        }
    }
}
