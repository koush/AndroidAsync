package com.koushikdutta.async.test;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.NameValuePair;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.body.MultipartFormDataBody;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.koushikdutta.async.util.StreamUtility;

import junit.framework.TestCase;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class HttpServerTests extends TestCase {
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
        
        httpServer.get("/hello", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                assertNotNull(request.getHeaders().get("Host"));
                response.send("hello");
            }
        });

        httpServer.post("/echo", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                try {
                    assertNotNull(request.getHeaders().get("Host"));
                    JSONObject json = new JSONObject();
                    if (request.getBody() instanceof UrlEncodedFormBody) {
                        UrlEncodedFormBody body = (UrlEncodedFormBody)request.getBody();
                        for (NameValuePair pair: body.get()) {
                            json.put(pair.getName(), pair.getValue());
                        }
                    }
                    else if (request.getBody() instanceof JSONObjectBody) {
                        json = ((JSONObjectBody)request.getBody()).get();
                    }
                    else if (request.getBody() instanceof StringBody) {
                        json.put("foo", ((StringBody)request.getBody()).get());
                    }
                    else if (request.getBody() instanceof MultipartFormDataBody) {
                        MultipartFormDataBody body = (MultipartFormDataBody)request.getBody();
                        for (NameValuePair pair: body.get()) {
                            json.put(pair.getName(), pair.getValue());
                        }
                    }

                    response.send(json);
                }
                catch (Exception e) {
                }
            }
        });
    }

    public void testJSONObject() throws Exception {
        JSONObject json = new JSONObject();
        json.put("foo", "bar");
        JSONObjectBody body = new JSONObjectBody(json);
        AsyncHttpPost post = new AsyncHttpPost("http://localhost:5000/echo");
        post.setBody(body);
        json = AsyncHttpClient.getDefaultInstance().executeJSONObject(post, null).get();
        assertEquals(json.getString("foo"), "bar");
    }

    public void testString() throws Exception {
        StringBody body = new StringBody("bar");
        AsyncHttpPost post = new AsyncHttpPost("http://localhost:5000/echo");
        post.setBody(body);
        JSONObject json = AsyncHttpClient.getDefaultInstance().executeJSONObject(post, null).get();
        assertEquals(json.getString("foo"), "bar");
    }

//    public void testUrlEncodedFormBody() throws Exception {
//        List<NameValuePair> params = new ArrayList<NameValuePair>();
//        params.add(new BasicNameValuePair("foo", "bar"));
//        HttpPost post = new HttpPost("http://localhost:5000/echo");
//        post.setEntity(new UrlEncodedFormEntity(params));
//
//        HttpResponse response = new DefaultHttpClient().execute(post);
//        String contents = StreamUtility.readToEnd(response.getEntity().getContent());
//        JSONObject json = new JSONObject(contents);
//        assertEquals(json.getString("foo"), "bar");
//    }
    
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
        AsyncServer.getDefault().stop();
    }
}
