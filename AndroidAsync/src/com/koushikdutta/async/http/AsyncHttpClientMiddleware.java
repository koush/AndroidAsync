package com.koushikdutta.async.http;

import android.os.Bundle;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.Cancelable;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public interface AsyncHttpClientMiddleware {
    public Cancelable getSocket(Bundle state, AsyncHttpRequest request, final ConnectCallback callback);
    public AsyncSocket onSocket(Bundle state, AsyncSocket socket, AsyncHttpRequest request);
    public void onHeadersReceived(Bundle state, AsyncSocket socket, AsyncHttpRequest request, ResponseHeaders headers);
    public void onRequestComplete(Bundle state, AsyncSocket socket, AsyncHttpRequest request, ResponseHeaders headers, Exception ex);
}
