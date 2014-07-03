package com.koushikdutta.async.test;

import android.test.AndroidTestCase;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONObject;

import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by koush on 6/4/13.
 */
public class SSLTests extends AndroidTestCase {
    public void testKeys() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        ks.load(getContext().getResources().openRawResource(R.raw.keystore), "storepass".toCharArray());
        kmf.init(ks, "storepass".toCharArray());


        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
        ts.load(getContext().getResources().openRawResource(R.raw.keystore), "storepass".toCharArray());
        tmf.init(ts);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        AsyncHttpServer httpServer = new AsyncHttpServer();
        httpServer.listenSecure(8888, sslContext);
        httpServer.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.send("hello");
            }
        });

        Thread.sleep(1000);

        AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setSSLContext(sslContext);
        AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setTrustManagers(tmf.getTrustManagers());
        AsyncHttpClient.getDefaultInstance().executeString(new AsyncHttpGet("https://localhost:8888/"), null).get();
    }

    public void disabled__testClientCertificateIssue163() throws Exception {
        // https://security.springthroughtest.com/hello.json

        AsyncServer server = new AsyncServer();
        try {
            AsyncHttpClient client = new AsyncHttpClient(server);
            JSONObject json = client.executeJSONObject(new AsyncHttpGet("https://security.springthroughtest.com/hello.json"), null).get();

        }
        finally {
            server.stop();
        }
    }
}
