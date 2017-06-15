package com.koushikdutta.async.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public interface AsyncSSLEngineConfigurator {
    SSLEngine createEngine(SSLContext sslContext, String peerHost, int peerPort);
    void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port);
}
