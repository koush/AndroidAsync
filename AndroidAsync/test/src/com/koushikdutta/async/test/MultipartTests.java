package com.koushikdutta.async.test;

import android.os.Environment;
import android.test.AndroidTestCase;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.MultipartFormDataBody;
import com.koushikdutta.async.http.body.MultipartFormDataBody.MultipartCallback;
import com.koushikdutta.async.http.body.Part;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

public class MultipartTests extends AndroidTestCase {
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
        
        httpServer.post("/", new HttpServerRequestCallback() {
            int gotten = 0;
            @Override
            public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                final MultipartFormDataBody body = (MultipartFormDataBody)request.getBody();
                body.setMultipartCallback(new MultipartCallback() {
                    @Override
                    public void onPart(Part part) {
                        if (part.isFile()) {
                            body.setDataCallback(new DataCallback() {
                                @Override
                                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                    gotten += bb.remaining();
                                    bb.recycle();
                                }
                            });
                        }
                    }
                });

                request.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        response.send(body.getField("baz") + gotten + body.getField("foo"));
                    }
                });
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        
        httpServer.stop();
        AsyncServer.getDefault().stop();
    }

    public void testUpload() throws Exception {
        File dummy = getContext().getFileStreamPath("dummy.txt");
        final String FIELD_VAL = "bar";
        dummy.getParentFile().mkdirs();
        FileOutputStream fout = new FileOutputStream(dummy);
        byte[] zeroes = new byte[100000];
        for (int i = 0; i < 10; i++) {
            fout.write(zeroes);
        }
        fout.close();
//        StreamUtility.writeFile(dummy, DUMMY_VAL);
        
        AsyncHttpPost post = new AsyncHttpPost("http://localhost:5000");
        MultipartFormDataBody body = new MultipartFormDataBody();
        body.addStringPart("foo", FIELD_VAL);
        body.addFilePart("my-file", dummy);
        body.addStringPart("baz", FIELD_VAL);
        post.setBody(body);

        Future<String> ret = AsyncHttpClient.getDefaultInstance().executeString(post, new StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
            }
        });
        
        String data = ret.get(10000, TimeUnit.MILLISECONDS);
        assertEquals(data, FIELD_VAL + (zeroes.length * 10) + FIELD_VAL);
    }
}
