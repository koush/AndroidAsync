package com.koushikdutta.async.test;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import junit.framework.TestCase;

/**
 * Created by koush on 11/4/13.
 */
public class RedirectTests extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        AsyncHttpServer server = new AsyncHttpServer();
        server.listen(6003);
        server.get("/foo", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                response.redirect("/bar");
            }
        });
        server.get("/bar", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                response.send("BORAT!");
            }
        });

        server.get("/foo/poo", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                response.redirect("../poo");
            }
        });
        server.get("/poo", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                response.send("SWEET!");
            }
        });
        server.get("/foo/bar", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                response.redirect("baz");
            }
        });
        server.get("/foo/baz", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                response.send("SUCCESS!");
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        AsyncServer.getDefault().stop();
    }

    public void testRelativeRedirect() throws Exception {
        String ret = AsyncHttpClient.getDefaultInstance()
        .executeString(new AsyncHttpGet("http://localhost:6003/foo/bar"), null)
        .get();

        assertEquals(ret, "SUCCESS!");

        ret = AsyncHttpClient.getDefaultInstance()
        .executeString(new AsyncHttpGet("http://localhost:6003/foo"), null)
        .get();

        assertEquals(ret, "BORAT!");

        ret = AsyncHttpClient.getDefaultInstance()
        .executeString(new AsyncHttpGet("http://localhost:6003/foo/poo"), null)
        .get();

        assertEquals(ret, "SWEET!");
    }
}
