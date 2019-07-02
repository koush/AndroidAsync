package com.koushikdutta.async.test;

import android.content.res.AssetManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.HttpDate;
import com.koushikdutta.async.http.cache.ResponseCacheMiddleware;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by koush on 6/13/13.
 */
@RunWith(AndroidJUnit4.class)
public class CacheTests {
    public void testMaxAgePrivate() throws Exception {
        AsyncHttpClient client = new AsyncHttpClient(AsyncServer.getDefault());
        ResponseCacheMiddleware cache = ResponseCacheMiddleware.addCache(client, new File(getContext().getFilesDir(), "AndroidAsyncTest"), 1024 * 1024 * 10);
        AsyncHttpServer httpServer = new AsyncHttpServer();
        try {
            httpServer.get("/uname/(.*)", new HttpServerRequestCallback() {
                @Override
                public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                    response.getHeaders().set("Date", HttpDate.format(new Date()));
                    response.getHeaders().set("Cache-Control", "private, max-age=10000");
                    response.send(request.getMatcher().group(1));
                }
            });

            AsyncServerSocket socket = httpServer.listen(AsyncServer.getDefault(), 0);
            int port = socket.getLocalPort();
            // clear the old cache
            cache.clear();

            client.executeString(new AsyncHttpGet("http://localhost:" + port + "/uname/43434"), null).get();

            client.executeString(new AsyncHttpGet("http://localhost:" + port + "/uname/43434"), null).get();


            assertEquals(cache.getCacheHitCount(), 1);
            assertEquals(cache.getNetworkCount(), 1);
        }
        finally {
            AsyncServer.getDefault().stop();
            client.getMiddleware().remove(cache);
        }
    }

    final static String dataNameAndHash = "6691924d7d24237d3b3679310157d640";
    @Test
    public void test304() throws Exception {
        try {
            AsyncHttpServer httpServer = new AsyncHttpServer();
            AsyncServerSocket socket = httpServer.listen(AsyncServer.getDefault(), 0);
            int port = socket.getLocalPort();

            AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
            httpServer.directory(InstrumentationRegistry.getTargetContext(), "/.*?", "");

            AsyncHttpClient client = new AsyncHttpClient(AsyncServer.getDefault());
            ByteBufferList bb = client.executeByteBufferList(new AsyncHttpGet("http://localhost:" + port + "/" + dataNameAndHash), new AsyncHttpClient.DownloadCallback() {
                @Override
                public void onCompleted(Exception e, AsyncHttpResponse source, ByteBufferList result) {
                    System.out.println(source.headers());
                }
            })
                    .get();
        }
        finally {
            AsyncServer.getDefault().stop();
        }
    }

    private static final long TIMEOUT = 1000L;
    public void testFilteredDataEmitter() throws Exception {
        final Semaphore semaphore = new Semaphore(0);

        FilteredDataEmitter f = new FilteredDataEmitter() {
            @Override
            public boolean isPaused() {
                return false;
            }
        };

        f.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                assertEquals(bb.readString(), "hello");
                bb.recycle();
                semaphore.release();
            }
        });

        f.onDataAvailable(f, new ByteBufferList().add(ByteBuffer.wrap("hello".getBytes())));
        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));

        f.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                fail();
            }
        });
        f.close();

        f.onDataAvailable(f, new ByteBufferList().add(ByteBuffer.wrap("hello".getBytes())));
    }
}
