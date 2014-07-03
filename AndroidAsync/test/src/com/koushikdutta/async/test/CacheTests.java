package com.koushikdutta.async.test;

import android.os.Environment;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.ResponseCacheMiddleware;
import com.koushikdutta.async.http.libcore.HttpDate;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import junit.framework.TestCase;

import java.io.File;
import java.util.Date;

/**
 * Created by koush on 6/13/13.
 */
public class CacheTests extends TestCase {
    public void testMaxAgePrivate() throws Exception {
        AsyncHttpClient client = new AsyncHttpClient(AsyncServer.getDefault());
        ResponseCacheMiddleware cache = ResponseCacheMiddleware.addCache(client, new File(Environment.getExternalStorageDirectory(), "AndroidAsyncTest"), 1024 * 1024 * 10);
        AsyncHttpServer httpServer = new AsyncHttpServer();
        try {
            httpServer.get("/uname/(.*)", new HttpServerRequestCallback() {
                @Override
                public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                    response.getHeaders().getHeaders().set("Date", HttpDate.format(new Date()));
                    response.getHeaders().getHeaders().set("Cache-Control", "private, max-age=10000");
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

//    static public boolean deleteDirectory(File path) {
//        if (path.exists()) {
//            File[] files = path.listFiles();
//            if (files != null) {
//                for (int i = 0; i < files.length; i++) {
//                    if (files[i].isDirectory()) {
//                        deleteDirectory(files[i]);
//                    } else {
//                        files[i].delete();
//                    }
//                }
//            }
//        }
//        return (path.delete());
//    }

//    public void testDiskLruCache() throws Exception {
//        File dir = new File(Environment.getExternalStorageDirectory(), "AndroidAsyncTest/cache-test");
//        deleteDirectory(dir);
//        DiskLruCache cache = DiskLruCache.open(dir, 0, 1000, 10000000);
//        DiskLruCache.Editor editor = cache.edit("stuff");
//
//        DiskLruCache cache2 = DiskLruCache.open(dir, 0, 2, 10000000);
//        DiskLruCache.Snapshot snapshot = cache2.get("stuff");
//    }
}
