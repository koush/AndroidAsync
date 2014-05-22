package com.koushikdutta.async.test;

import android.net.Uri;
import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import junit.framework.TestCase;

import java.net.URI;
import java.util.concurrent.TimeoutException;

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
                }, 1000);
            }
        });

        server.post("/now", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                StringBody body = (StringBody)request.getBody();
                response.send(body.get());
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
        server.stop();
        AsyncServer.getDefault().stop();
    }

    public void testTimeout() throws Exception {
        AsyncHttpRequest req = new AsyncHttpRequest(Uri.parse("http://localhost:5000/3"), "GET");
        req.setTimeout(1000);
        try {
            AsyncHttpClient.getDefaultInstance().executeString(req, null).get();
            fail();
        }
        catch (Exception e) {
            Log.d("timeout", "error", e);
            assertTrue(e.getCause() instanceof TimeoutException);
        }

        req = new AsyncHttpRequest(Uri.parse("http://localhost:5000/3"), "GET");
        assertEquals("3", AsyncHttpClient.getDefaultInstance().executeString(req, null).get());
    }

    public void testSlowBody() throws Exception {
        AsyncHttpRequest req = new AsyncHttpRequest(Uri.parse("http://localhost:5000/now"), "POST");
        req.setTimeout(1000);
        req.setLogging("slowbody", Log.VERBOSE);
        req.setBody(new DelayedStringBody("foo"));
        assertEquals("foo", AsyncHttpClient.getDefaultInstance().executeString(req, null).get());

        req = new AsyncHttpRequest(Uri.parse("http://localhost:5000/3"), "GET");
        req.setLogging("slowbody", Log.VERBOSE);
        req.setTimeout(100);
        req.setBody(new DelayedStringBody("foo"));
        try {
            AsyncHttpClient.getDefaultInstance().executeString(req, null).get();
            fail();
        }
        catch (Exception e) {
            Log.d("timeout", "error", e);
            assertTrue(e.getCause() instanceof TimeoutException);
        }
    }

    class DelayedStringBody extends StringBody {
        public DelayedStringBody(String value) {
            super(value);
        }
        @Override
        public void write(final AsyncHttpRequest request, final DataSink sink, final CompletedCallback completed) {
            AsyncServer.getDefault().postDelayed(new Runnable() {
                @Override
                public void run() {
                    DelayedStringBody.super.write(request, sink, completed);
                }
            }, 1000);
        }
    }
}
