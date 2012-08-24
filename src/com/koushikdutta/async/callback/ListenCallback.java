package com.koushikdutta.async.callback;

import com.koushikdutta.async.AsyncSocket;


public interface ListenCallback {
    public void onAccepted(AsyncSocket handler);
}
