package com.koushikdutta.async.test;

import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import junit.framework.TestCase;

import java.util.concurrent.CountDownLatch;


public class IssueWithWebSocketFuturesTests extends TestCase {

    //testing that websocket callback gets called with the correct parameters.
    public void testWebSocketFutureWithHandshakeFailureCallback() throws Exception {

        //creating a faulty server!
        AsyncHttpServer httpServer = new AsyncHttpServer();
        httpServer.websocket(".*", new AsyncHttpServer.WebSocketRequestCallback() {
            @Override
            public void onConnected(WebSocket webSocket, AsyncHttpServerRequest request) {

            }
        });
        httpServer.listen(6666);



        final Exception[] callbackException = {null};
        final WebSocket[] callbackWs = {null};
        final CountDownLatch countDownLatch = new CountDownLatch(1);


        //for some reason, it fails with a WebSocketHandshakeException.
        //But in general, if the handshake fails, the callback must be called with an exception.
        Future<WebSocket> wsFuture = AsyncHttpClient.getDefaultInstance().websocket("ws://127.0.0.1:6666", "ws", new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                callbackException[0] = ex;
                callbackWs[0] = webSocket;
                countDownLatch.countDown();
            }
        });


        //wait for the future to complete
        countDownLatch.await();

        //exactly one mut be null
        assertTrue(callbackWs[0] == null ^ callbackException[0] == null);

        //callback parameters must be the same as the future's result
        assertEquals(wsFuture.tryGet(), callbackWs[0]);
        assertEquals(wsFuture.tryGetException(), callbackException[0]);

    }
}
