package com.koushikdutta.async.http.socketio;

public interface SocketIOConnectCallback {
    public void onConnectCompleted(Exception ex, SocketIOClient client);
}