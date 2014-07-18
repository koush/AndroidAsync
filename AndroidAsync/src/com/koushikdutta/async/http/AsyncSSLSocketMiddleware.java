package com.koushikdutta.async.http;

import android.net.Uri;
import android.text.TextUtils;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

public class AsyncSSLSocketMiddleware extends AsyncSocketMiddleware {
    public AsyncSSLSocketMiddleware(AsyncHttpClient client) {
        super(client, "https", 443);
    }

    protected SSLContext sslContext;

    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public SSLContext getSSLContext() {
        return sslContext != null ? sslContext : AsyncSSLSocketWrapper.getDefaultSSLContext();
    }

    protected TrustManager[] trustManagers;

    public void setTrustManagers(TrustManager[] trustManagers) {
        this.trustManagers = trustManagers;
    }

    protected HostnameVerifier hostnameVerifier;

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    protected List<AsyncSSLEngineConfigurator> engineConfigurators = new ArrayList<AsyncSSLEngineConfigurator>();

    public void addEngineConfigurator(AsyncSSLEngineConfigurator engineConfigurator) {
        engineConfigurators.add(engineConfigurator);
    }

    public void clearEngineConfigurators() {
        engineConfigurators.clear();
    }

    protected SSLEngine createConfiguredSSLEngine(String host, int port) {
        SSLContext sslContext = getSSLContext();
        SSLEngine sslEngine = sslContext.createSSLEngine();

        for (AsyncSSLEngineConfigurator configurator : engineConfigurators) {
            configurator.configureEngine(sslEngine, host, port);
        }

        return sslEngine;
    }

    protected AsyncSSLSocketWrapper.HandshakeCallback createHandshakeCallback(final ConnectCallback callback) {
        return new AsyncSSLSocketWrapper.HandshakeCallback() {
            @Override
            public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                callback.onConnectCompleted(e, socket);
            }
        };
    }

    protected void tryHandshake(final ConnectCallback callback, AsyncSocket socket, final Uri uri, final int port) {
        AsyncSSLSocketWrapper.handshake(socket, uri.getHost(), port,
        createConfiguredSSLEngine(uri.getHost(), port),
        trustManagers, hostnameVerifier, true,
        createHandshakeCallback(callback));
    }

    @Override
    protected ConnectCallback wrapCallback(final ConnectCallback callback, final Uri uri, final int port, final boolean proxied) {
        return new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, final AsyncSocket socket) {
                if (ex == null) {
                    if (!proxied) {
                        tryHandshake(callback, socket, uri, port);
                    }
                    else {
                        // this SSL connection is proxied, must issue a CONNECT request to the proxy server
                        // http://stackoverflow.com/a/6594880/704837
                        RawHeaders connect = new RawHeaders();
                        connect.setStatusLine(String.format("CONNECT %s:%s HTTP/1.1", uri.getHost(), port));
                        Util.writeAll(socket, connect.toHeaderString().getBytes(), new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                if (ex != null) {
                                    callback.onConnectCompleted(ex, socket);
                                    return;
                                }

                                LineEmitter liner = new LineEmitter();
                                liner.setLineCallback(new LineEmitter.StringCallback() {
                                    String statusLine;
                                    @Override
                                    public void onStringAvailable(String s) {
                                        if (statusLine == null) {
                                            statusLine = s;
                                            if (statusLine.length() > 128 || !statusLine.contains("200")) {
                                                socket.setDataCallback(null);
                                                socket.setEndCallback(null);
                                                callback.onConnectCompleted(new IOException("non 200 status line"), socket);
                                            }
                                        }
                                        else {
                                            socket.setDataCallback(null);
                                            socket.setEndCallback(null);
                                            if (TextUtils.isEmpty(s.trim())) {
                                                tryHandshake(callback, socket, uri, port);
                                            }
                                            else {
                                                callback.onConnectCompleted(new IOException("unknown second status line"), socket);
                                            }
                                        }
                                    }
                                });

                                socket.setDataCallback(liner);

                                socket.setEndCallback(new CompletedCallback() {
                                    @Override
                                    public void onCompleted(Exception ex) {
                                        if (!socket.isOpen() && ex == null)
                                            ex = new IOException("socket closed before proxy connect response");
                                        callback.onConnectCompleted(ex, socket);
                                    }
                                });

//                                AsyncSocket wrapper = socket;
//                                if (ex == null)
//                                    wrapper = new AsyncSSLSocketWrapper(socket, uri.getHost(), port, sslContext, trustManagers, hostnameVerifier, true);
//                                callback.onConnectCompleted(ex, wrapper);
                            }
                        });
                    }
                }
                else {
                    callback.onConnectCompleted(ex, socket);
                }
            }
        };
    }
}
