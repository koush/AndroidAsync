package com.koushikdutta.async.test;

import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
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
                    response.getHeaders().set("Transfer-Encoding", "");
                    response.code(200);
                    Util.writeAll(response, "foobarbeepboop".getBytes(), new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception ex) {
                            response.end();
                        }
                    });
                }
            });

            httpServer.listen(5959);

            AsyncHttpGet get = new AsyncHttpGet("http://localhost:5959/");
            get.setLogging("issue59", Log.VERBOSE);
            get.getHeaders().removeAll("Connection");
            get.getHeaders().removeAll("Accept-Encoding");

            assertEquals("foobarbeepboop", AsyncHttpClient.getDefaultInstance().executeString(get, null).get(1000, TimeUnit.MILLISECONDS));
        }
        finally {
            httpServer.stop();
            AsyncServer.getDefault().stop();
        }
    }

    public void testIon428() throws Exception {
        ByteBufferList bb = AsyncHttpClient.getDefaultInstance().executeByteBufferList(new AsyncHttpGet("https://cdn2.vox-cdn.com/thumbor/KxtZNw37jKNfxdA0hX5edHvbTBE=/0x0:2039x1359/800x536/cdn0.vox-cdn.com/uploads/chorus_image/image/44254028/lg-g-watch.0.0.jpg"), null)
        .get();
    }
}
