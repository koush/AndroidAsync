package com.koushikdutta.async.http;

import javax.net.ssl.SSLEngine;

public interface AsyncSSLEngineConfigurator {
    public void configureEngine(SSLEngine engine, String host, int port);
}
