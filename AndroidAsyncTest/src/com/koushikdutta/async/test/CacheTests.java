package com.koushikdutta.async.test;

import android.os.Environment;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.AsyncHttpClient;
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

            httpServer.listen(AsyncServer.getDefault(), 5555);
            // clear the old cache
            cache.clear();

            client.getString("http://localhost:5555/uname/43434").get();

            client.getString("http://localhost:5555/uname/43434").get();


            assertEquals(cache.getCacheHitCount(), 1);
            assertEquals(cache.getNetworkCount(), 1);
        }
        finally {
            AsyncServer.getDefault().stop();
            client.getMiddleware().remove(cache);
        }
    }
}
