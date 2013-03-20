package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.callback.ConnectCallback;

public interface AsyncHttpClientMiddleware {
    public boolean getSocket(final AsyncHttpRequest request, final ConnectCallback callback);
    public AsyncSocket onSocket(AsyncSocket socket);
}
