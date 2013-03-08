package com.koushikdutta.async.http;

import java.net.URI;
import java.net.URISyntaxException;

public class AsyncHttpPost extends AsyncHttpRequest {
    public static final String METHOD = "POST";
    
    public AsyncHttpPost(String uri) {
        this(URI.create(uri));
    }

    public AsyncHttpPost(URI uri) {
        super(uri, METHOD);
    }
}
