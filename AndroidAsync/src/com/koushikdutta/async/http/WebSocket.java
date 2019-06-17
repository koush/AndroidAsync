package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;

public interface WebSocket extends AsyncSocket {
    interface StringCallback {
        void onStringAvailable(String s);
    }
    interface PingCallback {
        void onPingReceived(String s);
    }
    interface PongCallback {
        void onPongReceived(String s);
    }

    void send(byte[] bytes);
    void send(String string);
    void send(byte [] bytes, int offset, int len);
    void ping(String message);
    void pong(String message);
    
    void setStringCallback(StringCallback callback);
    StringCallback getStringCallback();

    void setPingCallback(PingCallback callback);
    
    void setPongCallback(PongCallback callback);
    PongCallback getPongCallback();

    boolean isBuffering();
    String getProtocol();
    
    AsyncSocket getSocket();
}
