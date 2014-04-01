package com.koushikdutta.async.http.socketio;

import android.net.Uri;
import android.text.TextUtils;

import com.koushikdutta.async.http.AsyncHttpPost;

public class SocketIORequest extends AsyncHttpPost {
    static String socketIOContext = "/socket.io/1/";
    
    public String getSocketIOContext() {
        return socketIOContext;
    }
    
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

    public SocketIORequest(String uri, String endpoint, String query) {
        this(uri,endpoint,query,null);
    }
    
    public SocketIORequest(String uri, String endpoint, String query,String context) {
        super(Uri.parse(uri + (query == null ? "" : "?" + query)).buildUpon().encodedPath(TextUtils.isEmpty(context) ? socketIOContext
                : context).build().toString());
        this.endpoint = endpoint;
        this.query = query;
        SocketIORequest.socketIOContext = context;
    }
}
