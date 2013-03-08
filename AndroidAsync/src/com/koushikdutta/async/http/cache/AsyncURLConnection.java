package com.koushikdutta.async.http.cache;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import junit.framework.Assert;

public class AsyncURLConnection extends URLConnection {
    protected AsyncURLConnection(URL url) {
        super(url);
    }

    @Override
    public void connect() throws IOException {
        Assert.fail();
    }
}
