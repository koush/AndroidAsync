package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.callback.ConnectCallback;

public class AsyncSocketMiddleware implements AsyncHttpClientMiddleware {
    @Override
    public boolean getSocket(AsyncHttpRequest request, ConnectCallback callback) {
        
        return true;
    }

    @Override
    public AsyncSocket onSocket(AsyncSocket socket) {
        return null;
    }
}
