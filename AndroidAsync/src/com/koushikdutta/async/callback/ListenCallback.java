package com.koushikdutta.async.callback;

import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ExceptionCallback;


public interface ListenCallback extends ExceptionCallback {
    public void onAccepted(AsyncSocket handler);
    public void onListening(AsyncServerSocket socket);
}
