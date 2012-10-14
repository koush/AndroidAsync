package com.koushikdutta.async.http;

import java.net.URI;
import java.net.URISyntaxException;

public class AsyncHttpGet extends AsyncHttpRequest {
    public static final String METHOD = "GET";
    
    public AsyncHttpGet(String uri) throws URISyntaxException {
        super(new URI(uri), METHOD, null, null, null);
    }

    public AsyncHttpGet(URI uri) {
        super(uri, METHOD, null, null, null);
    }
}
