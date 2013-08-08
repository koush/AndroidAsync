package com.koushikdutta.async.http.socketio;

import org.json.JSONArray;

public interface EventCallback {
    public void onEvent(String event, JSONArray argument, Acknowledge acknowledge);
}