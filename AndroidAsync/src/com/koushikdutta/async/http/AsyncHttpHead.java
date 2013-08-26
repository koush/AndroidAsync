package com.koushikdutta.async.http;

import java.net.URI;

/**
 * Created by koush on 8/25/13.
 */
public class AsyncHttpHead extends AsyncHttpRequest {
    public AsyncHttpHead(URI uri) {
        super(uri, METHOD);
    }

    public static final String METHOD = "HEAD";
}
