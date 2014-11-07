package com.koushikdutta.async.http;

import javax.net.ssl.SSLEngine;

public interface AsyncSSLEngineConfigurator {
    public void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port);
}
