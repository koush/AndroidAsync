package com.koushikdutta.async.test;

import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

/**
 * Created by koush on 8/31/13.
 */
public class Issue59 extends TestCase {
    public void testIssue() throws Exception {
        try {
            AsyncHttpServer httpServer = new AsyncHttpServer();
            httpServer.get("/", new HttpServerRequestCallback() {
                @Override
                public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                    response.getHeaders().getHeaders().set("Transfer-Encoding", "");
                    Util.writeAll(response, "foobarbeepboop".getBytes(), new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception ex) {
                            response.close();
                        }
                    });
                }
            });

            httpServer.listen(5959);

            AsyncHttpGet get = new AsyncHttpGet("http://localhost:5959/");
            get.setLogging("issue59", Log.VERBOSE);

            assertEquals("foobarbeepboop", AsyncHttpClient.getDefaultInstance().executeString(get).get(1000, TimeUnit.MILLISECONDS));
        }
        finally {
            AsyncServer.getDefault().stop();
        }
    }
}
