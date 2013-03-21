package com.koushikdutta.async.http;

import android.os.Bundle;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.Cancelable;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public class SimpleMiddleware implements AsyncHttpClientMiddleware {
    @Override
    public Cancelable getSocket(Bundle state, AsyncHttpRequest request, ConnectCallback callback) {
        return null;
    }

    @Override
    public AsyncSocket onSocket(Bundle state, AsyncSocket socket, AsyncHttpRequest request) {
        return null;
    }

    @Override
    public void onHeadersReceived(Bundle state, AsyncSocket socket, AsyncHttpRequest request, ResponseHeaders headers) {
    }

    @Override
    public void onRequestComplete(Bundle state, AsyncSocket socket, AsyncHttpRequest request, ResponseHeaders headers, Exception ex) {
    }
}
