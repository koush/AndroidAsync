package com.koushikdutta.async.http.socketio;

import org.json.JSONArray;

public interface EventCallback {
    public void onEvent(JSONArray argument, Acknowledge acknowledge);
}