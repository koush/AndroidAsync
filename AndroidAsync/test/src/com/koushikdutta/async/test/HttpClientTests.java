package com.koushikdutta.async.test;

import android.net.Uri;
import androidx.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpHead;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.cache.ResponseCacheMiddleware;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.AsyncProxyServer;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class HttpClientTests {
    AsyncServer server = new AsyncServer();
    AsyncHttpClient client  = new AsyncHttpClient(server);

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
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
    @Test
    public void testHomepage() throws Exception {
        Future<String> ret = client.executeString(new AsyncHttpGet("http://google.com"), null);
        assertNotNull(ret.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testClockworkMod() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        client.execute("http://www.clockworkmod.com", new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                // make sure gzip decoding works, as that is generally what github sends.
                Assert.assertEquals("gzip", response.headers().get("Content-Encoding"));
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
    final static String githubPath = "raw.githubusercontent.com/koush/AndroidAsync/master/AndroidAsync/test/assets/";
    final static String github = "https://" + githubPath + dataNameAndHash;
    public void testGithubRandomData() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Md5 md5 = Md5.createInstance();
        AsyncHttpGet get = new AsyncHttpGet(github);
        get.setLogging("AsyncTest", Log.VERBOSE);
        client.execute(get, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                assertNull(ex);
                // make sure gzip decoding works, as that is generally what github sends.
                // this broke sometime in 03/2014
//                Assert.assertEquals("gzip", response.getHeaders().getContentEncoding());
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
        Future<ByteBufferList> bb = client.executeByteBufferList(new AsyncHttpGet(github), null);
        md5.update(bb.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(md5.digest(), dataNameAndHash);
    }

    public void testSni() throws Exception {
//        ProviderInstaller.installIfNeeded(getContext());
//        AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setSSLContext(SSLContext.getInstance("TLS"));

        // this server requires SNI as it serves multiple SSL certificates
        // LOLLIPOP_MR1 and lower requires SSLEngineSNIConfigurator to set the appropriate fields via reflection.
        // Higher than LOLLIPOP_MR1 can use createSSLEngine(host, port) as it is based off recent-ish versions of Conscrypt
        // Conscrypt, if it is being used in GPS ProviderInstaller, can also use createSSLEngine(host, port)
        Future<String> string = client.executeString(new AsyncHttpGet("https://koush.com/"), null);
        string.get(TIMEOUT, TimeUnit.MILLISECONDS);
    }

    public void testGithubHelloWithFuture() throws Exception {
        Future<String> string = client.executeString(new AsyncHttpGet("https://" + githubPath + "hello.txt"), null);
        assertEquals(string.get(TIMEOUT, TimeUnit.MILLISECONDS), "hello world");
    }

    public void testGithubHelloWithFutureCallback() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        client.executeString(new AsyncHttpGet("https://" + githubPath + "hello.txt"), null)
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
        future = client.executeString(new AsyncHttpGet("http://yahoo.com"), new StringCallback() {
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
        ResponseCacheMiddleware cache = ResponseCacheMiddleware.addCache(client, new File(getContext().getFilesDir(), "AndroidAsyncTest"), 1024 * 1024 * 10);
        try {
            // clear the old cache
            cache.clear();
            // populate the cache
            testGithubRandomData();
            // this should result in a conditional cache hit
            testGithubRandomData();
            assertEquals(cache.getCacheHitCount(), 1);
        }
        finally {
            client.getMiddleware().remove(cache);
        }
    }

    Future<File> fileFuture;
    public void testFileCancel() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        File f = getContext().getFileStreamPath("test.txt");
        fileFuture = client.executeFile(new AsyncHttpGet(github), f.getAbsolutePath(), new AsyncHttpClient.FileCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, File result) {
                fail();
            }

            @Override
            public void onProgress(AsyncHttpResponse response, long downloaded, long total) {
                semaphore.release();
            }
        });
        fileFuture.setCallback(new FutureCallback<File>() {
            @Override
            public void onCompleted(Exception e, File result) {
                assertTrue(e instanceof CancellationException);
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
        assertFalse(f.exists());
    }

    boolean wasProxied;
    public void testProxy() throws Exception {
        wasProxied = false;
        final AsyncServer proxyServer = new AsyncServer();
        try {
            AsyncProxyServer httpServer = new AsyncProxyServer(proxyServer) {
                @Override
                protected boolean onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                    wasProxied = true;
                    return super.onRequest(request, response);
                }
            };

            AsyncServerSocket socket = httpServer.listen(proxyServer, 0);

//            client.getSocketMiddleware().enableProxy("localhost", 5555);

            AsyncHttpGet get = new AsyncHttpGet("http://www.clockworkmod.com");
            get.enableProxy("localhost", socket.getLocalPort());

            Future<String> ret = client.executeString(get, null);
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
        AsyncHttpRequest request = new AsyncHttpRequest(Uri.parse("http://jpkc.seiee.sjtu.edu.cn/ds/ds2/Course%20lecture/chapter%2010.pdf"), AsyncHttpGet.METHOD);
        String requestLine = request.getRequestLine().toString();
        assertEquals("GET /ds/ds2/Course%20lecture/chapter%2010.pdf HTTP/1.1", requestLine);
    }

    public void testHEAD() throws Exception {
        AsyncHttpHead req = new AsyncHttpHead(Uri.parse("http://31.media.tumblr.com/9606dcaa33b6877b7c485040393b9392/tumblr_mrtnysMonE1r4vl1yo1_500.jpg"));
        Future<String> str = AsyncHttpClient.getDefaultInstance().executeString(req, null);
        assertTrue(TextUtils.isEmpty(str.get(TIMEOUT, TimeUnit.MILLISECONDS)));
    }

    public void testPostJsonObject() throws Exception {
        JSONObject post = new JSONObject();
        post.put("ping", "pong");
        AsyncHttpPost p = new AsyncHttpPost("https://koush.clockworkmod.com/test/echo");
        p.setBody(new JSONObjectBody(post));
        JSONObject ret = AsyncHttpClient.getDefaultInstance().executeJSONObject(p, null).get();
        assertEquals("pong", ret.getString("ping"));
    }
}
