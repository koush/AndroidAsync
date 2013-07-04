package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.callback.ConnectCallback;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.URI;

public class AsyncSSLSocketMiddleware extends AsyncSocketMiddleware {
    public AsyncSSLSocketMiddleware(AsyncHttpClient client) {
        super(client, "https", 443);
    }

    SSLContext sslContext;

    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    TrustManager[] trustManagers;

    public void setTrustManagers(TrustManager[] trustManagers) {
        this.trustManagers = trustManagers;
    }

    HostnameVerifier hostnameVerifier;

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    @Override
    protected ConnectCallback wrapCallback(final ConnectCallback callback, final URI uri, final int port) {
        return new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (ex == null) {
                    callback.onConnectCompleted(ex, new AsyncSSLSocketWrapper(socket, uri.getHost(), port, sslContext, trustManagers, hostnameVerifier, true));
                }
                else {
                    callback.onConnectCompleted(ex, socket);
                }
            }
        };
    }
}
