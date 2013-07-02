package com.koushikdutta.async.http.socketio;

import android.net.Uri;

import com.koushikdutta.async.http.AsyncHttpPost;

public class SocketIORequest extends AsyncHttpPost {
    public SocketIORequest(String uri) {
        this(uri, "socket.io", "");
    }

    String endpoint;
    public String getEndpoint() {
        return endpoint;
    }

    private SocketIORequest(String uri, String path, String endpoint) {
        super(Uri.parse(uri).buildUpon().encodedPath("/" + path + "/1/").build().toString());
        this.endpoint = endpoint;
    }

    public SocketIORequest(String uri, String endpoint) {
        this(uri, endpoint, endpoint);
    }
}