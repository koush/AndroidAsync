package com.koushikdutta.async.http;

import android.net.Uri;

public class AsyncHttpDelete extends AsyncHttpRequest {
    public static final String METHOD = "DELETE";

    public AsyncHttpDelete(String uri) {
        this(Uri.parse(uri));
    }

    public AsyncHttpDelete(Uri uri) {
        super(uri, METHOD);
    }
}
