package com.koushikdutta.async.http;

import java.net.URI;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.callback.ConnectCallback;

public class AsyncSSLSocketMiddleware extends AsyncSocketMiddleware {
    public AsyncSSLSocketMiddleware(AsyncHttpClient client) {
        super(client, "https", 443);
    }

    @Override
    protected ConnectCallback wrapCallback(final ConnectCallback callback, final URI uri, final int port) {
        return new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (ex == null) {
                    callback.onConnectCompleted(ex, new AsyncSSLSocket(socket, uri.getHost(), port));
                }
                else {
                    callback.onConnectCompleted(ex, socket);
                }
            }
        };
    }
}
