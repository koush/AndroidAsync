package com.koushikdutta.async.wrapper;

import com.koushikdutta.async.AsyncSocket;

public interface AsyncSocketWrapper extends AsyncSocket, DataEmitterWrapper {
    public AsyncSocket getSocket();
}
