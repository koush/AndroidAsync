package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import com.koushikdutta.async.http.SimpleMiddleware;

/**
 * Created by koush on 7/19/14.
 */
public class HttpTransport extends SimpleMiddleware {
    @Override
    public boolean sendHeaders(SendHeaderData data) {
        return super.sendHeaders(data);
    }

}
