package com.koushikdutta.async.callback;

import com.koushikdutta.async.AsyncSocket;

public interface ConnectCallback {
    public void onConnectCompleted(Exception ex, AsyncSocket socket);
}
