package com.koushikdutta.async.http.socketio;

import android.net.Uri;
import android.text.TextUtils;

import com.koushikdutta.async.http.AsyncHttpPost;

public class SocketIORequest extends AsyncHttpPost {
    public SocketIORequest(String uri) {
        this(uri, "");
    }

    String endpoint;
    public String getEndpoint() {
        return endpoint;
    }

    public SocketIORequest(String uri, String endpoint) {
        super(Uri.parse(uri).buildUpon().encodedPath("/socket.io/1/").build().toString());
        this.endpoint = endpoint;
    }
}
