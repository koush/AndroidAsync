package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;

public interface AsyncHttpClientMiddleware {
    public AsyncSocket getSocket(final AsyncHttpRequest request, final HttpConnectCallback callback);
    public AsyncSocket onSocket(AsyncSocket socket);
}
