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

    String query;
    public String getQuery() {
        return query;
    }

    public SocketIORequest(String uri, String endpoint) {
        this(uri, endpoint, null);
    }
    
    public SocketIORequest(String uri, String path, String endpoint, String query) {
        super(Uri.parse(uri + (query == null ? "" : "?" + query)).buildUpon().encodedPath(TextUtils.isEmpty(path) ? "/socket.io/1/" : "/" + path + "/1/").build().toString());
        this.endpoint = endpoint;
        this.query = query;
    }
    
    public SocketIORequest(String uri, String endpoint, String query) {
        this(uri, null, endpoint, query);
    }
    
}
