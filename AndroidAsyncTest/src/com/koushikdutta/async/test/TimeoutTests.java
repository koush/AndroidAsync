package com.koushikdutta.async.test;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import junit.framework.TestCase;

/**
 * Created by koush on 7/11/13.
 */
public class TimeoutTests extends TestCase {
    public TimeoutTests() {
        server.get("/3", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                // never respond
                AsyncServer.getDefault().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        response.send("3");
                    }
                }, 3000);
            }
        });

        server.get("/now", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.send("now");
            }
        });
    }
    AsyncHttpServer server = new AsyncHttpServer();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server.listen(AsyncServer.getDefault(), 5000);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        AsyncServer.getDefault().stop();
    }

    public void testTimeout() throws Exception {
    }
}
