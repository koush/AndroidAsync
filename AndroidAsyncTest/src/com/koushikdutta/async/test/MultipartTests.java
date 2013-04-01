package com.koushikdutta.async.test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import android.os.Environment;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.MultipartCallback;
import com.koushikdutta.async.http.MultipartFormDataBody;
import com.koushikdutta.async.http.Part;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

public class MultipartTests extends TestCase {
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
        
        httpServer.post("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                final ByteBufferList list = new ByteBufferList();
                final MultipartFormDataBody body = (MultipartFormDataBody)request.getBody();
                body.setMultipartCallback(new MultipartCallback() {
                    @Override
                    public void onPart(Part part) {
                        if (part.isFile()) {
                            body.setDataCallback(new DataCallback() {
                                @Override
                                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                    list.add(bb);
                                    bb.clear();
                                }
                            });
                        }
                    }
                });
                
                request.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        response.send(list.peekString() + body.getField("foo"));
                    }
                });
            }
        });
    }
    
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        
        httpServer.stop();
        server.stop();
    }

    public void testUpload() throws Exception {
        File dummy = new File(Environment.getExternalStorageDirectory(), "AndroidAsync/dummy.txt");
        final String DUMMY_VAL = "dummy";
        final String FIELD_VAL = "bar";
        StreamUtility.writeFile(dummy, DUMMY_VAL);
        
        AsyncHttpPost post = new AsyncHttpPost("http://localhost:5000");
//        AsyncHttpPost post = new AsyncHttpPost("http://192.168.1.2:3000");

        MultipartFormDataBody body = new MultipartFormDataBody();
        body.addFilePart("my-file", dummy);
        body.addStringPart("foo", FIELD_VAL);
        post.setBody(body);

        Future<String> ret = AsyncHttpClient.getDefaultInstance().execute(post, (StringCallback)null);
        
        assertEquals(ret.get(5000000, TimeUnit.MILLISECONDS), DUMMY_VAL + FIELD_VAL);
    }
}
