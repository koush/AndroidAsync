package com.koushikdutta.async.http.callback;

import com.koushikdutta.async.http.cache.RawHeaders;

/**
 * Created by koush on 6/30/13.
 */
public interface HeadersCallback {
    public void onHeaders(RawHeaders headers);
}
