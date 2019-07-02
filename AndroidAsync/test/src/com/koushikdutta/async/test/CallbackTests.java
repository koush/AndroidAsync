package com.koushikdutta.async.test;

import androidx.test.runner.AndroidJUnit4;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
@RunWith(AndroidJUnit4.class)
public class CallbackTests {
    @Test
    public void testThrow() throws Exception {
        int port = AsyncServer.getDefault().listen(null, 0, new ListenCallback() {
            @Override
            public void onAccepted(AsyncSocket socket) {
                Util.writeAll(socket, "poop".getBytes(), new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {

                    }
                });

                socket.setDataCallback(new DataCallback.NullDataCallback());
            }

            @Override
            public void onListening(AsyncServerSocket socket) {

            }

            @Override
            public void onCompleted(Exception ex) {

            }
        }).getLocalPort();



        AsyncServer.getDefault().connectSocket("localhost", port, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                socket.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        bb.recycle();
                        throw new NullPointerException("this should crash?");

                    }
                });
            }
        });

        Thread.sleep(1000000);
        fail();
    }

    @Test
    public void testHttpServerThrow() throws Exception {
        AsyncHttpServer server = new AsyncHttpServer();
        int port = server.listen(0).getLocalPort();

        server.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                AsyncHttpClient.getDefaultInstance().executeString(new AsyncHttpGet("https://google.com"), null)
                .setCallback(new FutureCallback<String>() {
                    @Override
                    public void onCompleted(Exception e, String result) {
                        throw new NullPointerException();
                    }
                });
            }
        });

        String result = AsyncHttpClient.getDefaultInstance().executeString(new AsyncHttpGet("http://localhost:" + port + "/"), null).get();

        Thread.sleep(100000000);
        fail();
    }
}
