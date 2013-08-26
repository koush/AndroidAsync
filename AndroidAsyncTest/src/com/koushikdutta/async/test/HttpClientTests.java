package com.koushikdutta.async.test;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.*;
import com.koushikdutta.async.http.AsyncHttpClient.DownloadCallback;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.File;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HttpClientTests extends TestCase {
    AsyncHttpClient client;
    AsyncServer server = new AsyncServer();
    
    public HttpClientTests() {
        super();
        server.setAutostart(true);
        client = new AsyncHttpClient(server);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        client.getSSLSocketMiddleware().setConnectAllAddresses(false);
        client.getSocketMiddleware().setConnectAllAddresses(false);
        client.getSocketMiddleware().disableProxy();
        server.stop();
    }

    /*
    public void testConnectAllAddresses() throws Exception {
        assertEquals(client.getSSLSocketMiddleware().getConnectionPoolCount(), 0);
        assertEquals(client.getSocketMiddleware().getConnectionPoolCount(), 0);

        client.getSSLSocketMiddleware().setConnectAllAddresses(true);
        client.getSocketMiddleware().setConnectAllAddresses(true);

        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        AsyncHttpGet get = new AsyncHttpGet("http://www.clockworkmod.com");
        get.setLogging("ConnectionPool", Log.VERBOSE);
        client.execute(get, new HttpConnectCallback() {
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

        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));

        long start = System.currentTimeMillis();
        while (client.getSocketMiddleware().getConnectionPoolCount() != 2) {
            Thread.sleep(50);
            if (start + 5000L < System.currentTimeMillis())
                fail();
        }
    }
    */

    private static final long TIMEOUT = 10000L;
    public void testHomepage() throws Exception {
        Future<String> ret = client.get("http://google.com", (StringCallback)null);
        assertNotNull(ret.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    public void testClockworkMod() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        client.execute("http://www.clockworkmod.com", new HttpConnectCallback() {
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

        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    // this testdata file was generated using /dev/random. filename is also the md5 of the file.
    final static String dataNameAndHash = "6691924d7d24237d3b3679310157d640";
    final static String githubPath = "github.com/koush/AndroidAsync/raw/master/AndroidAsyncTest/testdata/";
    final static String github = "https://" + githubPath + dataNameAndHash;
    final static String githubInsecure = "http://" + githubPath + dataNameAndHash;
    public void testGithubRandomData() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        AsyncHttpGet get = new AsyncHttpGet(github);
        get.setLogging("AsyncTest", Log.DEBUG);
        client.execute(get, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                assertNull(ex);
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
        
        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }
    
    public void testGithubRandomDataWithFuture() throws Exception {
        final Md5 md5 = Md5.createInstance();
        Future<ByteBufferList> bb = client.get(github, (DownloadCallback)null);
        md5.update(bb.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }

    public void testInsecureGithubRandomDataWithFuture() throws Exception {
        final Md5 md5 = Md5.createInstance();
        Future<ByteBufferList> bb = client.get(githubInsecure, (DownloadCallback)null);
        md5.update(bb.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }

    public void testInsecureGithubRandomDataWithFutureCallback() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        client.executeByteBufferList(new AsyncHttpGet(githubInsecure).setHandler(null), null).setCallback(new FutureCallback<ByteBufferList>() {
            @Override
            public void onCompleted(Exception e, ByteBufferList bb) {
                md5.update(bb);
                semaphore.release();
            }
        });
        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }

    public void testGithubHelloWithFuture() throws Exception {
        Future<String> string = client.get("https://" + githubPath + "hello.txt", (StringCallback)null);
        assertEquals(string.get(TIMEOUT, TimeUnit.MILLISECONDS), "hello world");
    }

    public void testGithubHelloWithFutureCallback() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        client.executeString(new AsyncHttpGet("https://" + githubPath + "hello.txt").setHandler(null))
        .setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String result) {
                assertEquals(result, "hello world");
                semaphore.release();
            }
        });
        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    Future<String> future;
    public void testCancel() throws Exception {
        future = AsyncHttpClient.getDefaultInstance().get("http://yahoo.com", new StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                fail();
            }
            
            @Override
            public void onConnect(AsyncHttpResponse response) {
                future.cancel();
            }
        });

        try {
            future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            // this should never reach here as it was cancelled
            fail();
        }
        catch (TimeoutException e) {
            // timeout should also fail, since it was cancelled
            fail();
        }
        catch (ExecutionException e) {
            // execution exception is correct, make sure inner exception is cancellation
            assertTrue(e.getCause() instanceof CancellationException);
        }
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

    Future<File> fileFuture;
    public void testFileCancel() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        fileFuture = client.getFile(github, "/sdcard/hello.txt", new AsyncHttpClient.FileCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, File result) {
                fail();
            }

            @Override
            public void onProgress(AsyncHttpResponse response, int downloaded, int total) {
                semaphore.release();
            }
        })
        .setCallback(new FutureCallback<File>() {
            @Override
            public void onCompleted(Exception e, File result) {
                fail();
            }
        });

        try {
            assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
            assertTrue(fileFuture.cancel());
            fileFuture.get();
            fail();
        }
        catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof CancellationException);
        }
//        Thread.sleep(1000);
//        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(new File("/sdcard/hello.txt").exists());
    }

    boolean wasProxied;
    public void testProxy() throws Exception {
        wasProxied = false;
        final AsyncServer proxyServer = new AsyncServer();
        try {
            AsyncHttpServer httpServer = new AsyncHttpServer();
            httpServer.get(".*", new HttpServerRequestCallback() {
                @Override
                public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                    Log.i("Proxy", "Proxying request");
                    wasProxied = true;
                    AsyncHttpClient proxying = new AsyncHttpClient(proxyServer);

                    String url = request.getPath();
                    proxying.get(url, new StringCallback() {
                        @Override
                        public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                            response.send(result);
                        }
                    });
                }
            });

            httpServer.listen(proxyServer, 5555);

//            client.getSocketMiddleware().enableProxy("localhost", 5555);

            AsyncHttpGet get = new AsyncHttpGet("http://www.clockworkmod.com");
            get.enableProxy("localhost", 5555);

            Future<String> ret = client.executeString(get);
            String data;
            assertNotNull(data = ret.get(TIMEOUT, TimeUnit.MILLISECONDS));
            assertTrue(data.contains("ClockworkMod"));
            assertTrue(wasProxied);
        }
        finally {
            proxyServer.stop();
        }
    }

    public void testUriPathWithSpaces() throws Exception {
        AsyncHttpRequest request = new AsyncHttpRequest(URI.create("http://jpkc.seiee.sjtu.edu.cn/ds/ds2/Course%20lecture/chapter%2010.pdf"), AsyncHttpGet.METHOD);
        String requestLine = request.getRequestLine().toString();
        assertEquals("GET /ds/ds2/Course%20lecture/chapter%2010.pdf HTTP/1.1", requestLine);
    }

    public void testHEAD() throws Exception {
        AsyncHttpHead req = new AsyncHttpHead(URI.create("http://31.media.tumblr.com/9606dcaa33b6877b7c485040393b9392/tumblr_mrtnysMonE1r4vl1yo1_500.jpg"));
        Future<String> str = AsyncHttpClient.getDefaultInstance().execute(req, (StringCallback)null);
        assertTrue(TextUtils.isEmpty(str.get(TIMEOUT, TimeUnit.MILLISECONDS)));
    }
}
