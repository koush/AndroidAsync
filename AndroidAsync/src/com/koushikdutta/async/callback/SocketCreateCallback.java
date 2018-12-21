package com.koushikdutta.async.callback;

import com.koushikdutta.async.AsyncNetworkSocket;

public interface SocketCreateCallback {
    void onSocketCreated(int localPort);
}
