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
        AsyncHttpServer httpServer = new AsyncHttpServer();
        try {
            httpServer.get("/", new HttpServerRequestCallback() {
                @Override
                public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                    // setting this to empty is a hacky way of telling the framework not to use
                    // transfer-encoding. It will get removed.
                    response.getHeaders().getHeaders().set("Transfer-Encoding", "");
                    response.responseCode(200);
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
            get.getHeaders().getHeaders().removeAll("Connection");
            get.getHeaders().getHeaders().removeAll("Accept-Encoding");

            assertEquals("foobarbeepboop", AsyncHttpClient.getDefaultInstance().executeString(get).get(1000, TimeUnit.MILLISECONDS));
        }
        finally {
            httpServer.stop();
            AsyncServer.getDefault().stop();
        }
    }
}
