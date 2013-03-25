package com.koushikdutta.async.test;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import junit.framework.TestCase;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

public class HttpServerTests extends TestCase {
    AsyncHttpServer httpServer;
    AsyncServer server;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        server = new AsyncServer();
        server.setAutostart(true);

        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                fail();
            }
        });
        httpServer.listen(server, 5000);
        
        httpServer.get("/hello", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.send("hello");
            }
        });
    }
    
    public HttpServerTests() {
        super();
    }
    
    public void testServerHello() throws Exception {
        URL url = new URL("http://localhost:5000/hello");
        URLConnection conn = url.openConnection();
        
        InputStream is = conn.getInputStream();
        
        String contents = StreamUtility.readToEnd(is);
        is.close();
        assertEquals(contents, "hello");
    }
    
    public void testServerHelloAgain() throws Exception {
        URL url = new URL("http://localhost:5000/hello");
        URLConnection conn = url.openConnection();
        
        InputStream is = conn.getInputStream();
        
        String contents = StreamUtility.readToEnd(is);
        is.close();
        assertEquals(contents, "hello");
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        
        httpServer.stop();
        server.stop();
    }
}
