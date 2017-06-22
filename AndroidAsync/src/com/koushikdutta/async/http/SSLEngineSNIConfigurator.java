package com.koushikdutta.async.http;

import android.os.Build;

import java.lang.reflect.Field;
import java.util.Hashtable;

import javax.net.ssl.SSLContext;
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
        boolean skipReflection;

        @Override
        public SSLEngine createEngine(SSLContext sslContext, String peerHost, int peerPort) {
            return null;
        }

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
            if (useSni == null || skipReflection)
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
    public SSLEngine createEngine(SSLContext sslContext, String peerHost, int peerPort) {
        // pre M, must use reflection to enable SNI, otherwise createSSLEngine(peerHost, peerPort) works.
        SSLEngine engine;
        boolean skipReflection = "GmsCore_OpenSSL".equals(sslContext.getProvider().getName()) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        if (skipReflection)
            engine = sslContext.createSSLEngine(peerHost, peerPort);
        else
            engine = sslContext.createSSLEngine();
//        ensureHolder(engine).skipReflection = skipReflection;
        return engine;
    }

    EngineHolder ensureHolder(SSLEngine engine) {
        String name = engine.getClass().getCanonicalName();
        EngineHolder holder = holders.get(name);
        if (holder == null) {
            holder = new EngineHolder(engine.getClass());
            holders.put(name, holder);
        }
        return holder;
    }

    @Override
    public void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
        EngineHolder holder = ensureHolder(engine);
        holder.configureEngine(engine, data, host, port);
    }
}
