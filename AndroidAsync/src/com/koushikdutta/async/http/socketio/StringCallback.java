package com.koushikdutta.async.http.socketio;

public interface StringCallback {
    public void onString(String string, Acknowledge acknowledge);
}