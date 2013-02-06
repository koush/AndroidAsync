package com.koushikdutta.async.http;

import java.net.URI;

public class AsyncHttpGet extends AsyncHttpRequest {
    public static final String METHOD = "GET";
    
    public AsyncHttpGet(String uri) {
        super(URI.create(uri), METHOD);
    }

    public AsyncHttpGet(URI uri) {
        super(uri, METHOD);
    }
}
