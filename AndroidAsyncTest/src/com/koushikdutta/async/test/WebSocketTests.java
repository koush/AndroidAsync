package com.koushikdutta.async.test;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.WebSocket.StringCallback;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServer.WebSocketRequestCallback;

import junit.framework.TestCase;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class WebSocketTests extends TestCase {
    AsyncHttpServer httpServer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                fail();
            }
        });
        httpServer.listen(AsyncServer.getDefault(), 5000);
        
    
        httpServer.websocket("/ws", new WebSocketRequestCallback() {
            @Override
            public void onConnected(final WebSocket webSocket, RequestHeaders headers) {
                webSocket.setStringCallback(new StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        webSocket.send(s);
                    }
                });
            }
        });
    }
    
    private static final long TIMEOUT = 10000L; 
    public void testWebSocket() throws Exception {
        final Semaphore semaphore = new Semaphore(0);

        AsyncHttpClient.getDefaultInstance().websocket("http://localhost:5000/ws", null, new WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                webSocket.send("hello");
                webSocket.setStringCallback(new StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        assertEquals(s, "hello");
                        semaphore.release();
                    }
                });
            }
        });
        
        assertTrue(semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        
        httpServer.stop();
    }
}
