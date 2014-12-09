package com.koushikdutta.async.http;

import java.lang.reflect.Field;
import java.util.Hashtable;

import javax.net.ssl.SSLEngine;

/**
 * Created by koush on 12/8/14.
 */
public class SSLEngineSNIConfigurator implements AsyncSSLEngineConfigurator {
    private static class EngineHolder implements AsyncSSLEngineConfigurator {
        Field peerHost;
        Field peerPort;
        Field sslParameters;
        Field useSni;

        public EngineHolder(Class engineClass) {
            try {
                peerHost = engineClass.getSuperclass().getDeclaredField("peerHost");
                peerHost.setAccessible(true);

                peerPort = engineClass.getSuperclass().getDeclaredField("peerPort");
                peerPort.setAccessible(true);

                sslParameters = engineClass.getDeclaredField("sslParameters");
                sslParameters.setAccessible(true);

                useSni = sslParameters.getType().getDeclaredField("useSni");
                useSni.setAccessible(true);
            }
            catch (NoSuchFieldException e) {
            }
        }

        @Override
        public void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
            if (useSni == null)
                return;
            try {
                peerHost.set(engine, host);
                peerPort.set(engine, port);
                Object sslp = sslParameters.get(engine);
                useSni.set(sslp, true);
            }
            catch (IllegalAccessException e) {
            }
        }
    }

    Hashtable<String, EngineHolder> holders = new Hashtable<String, EngineHolder>();

    @Override
    public void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
        String name = engine.getClass().getCanonicalName();
        EngineHolder holder = holders.get(name);
        if (holder == null) {
            holder = new EngineHolder(engine.getClass());
            holders.put(name, holder);
        }

        holder.configureEngine(engine, data, host, port);
    }
}
