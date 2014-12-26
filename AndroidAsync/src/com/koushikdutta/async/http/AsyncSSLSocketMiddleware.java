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
import com.koushikdutta.async.http.cache.RawHeaders;

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

    protected SSLEngine createConfiguredSSLEngine(GetSocketData data, String host, int port) {
        SSLContext sslContext = getSSLContext();
        SSLEngine sslEngine = sslContext.createSSLEngine();

        for (AsyncSSLEngineConfigurator configurator : engineConfigurators) {
            configurator.configureEngine(sslEngine, data, host, port);
        }

        return sslEngine;
    }

    protected AsyncSSLSocketWrapper.HandshakeCallback createHandshakeCallback(final GetSocketData data, final ConnectCallback callback) {
        return new AsyncSSLSocketWrapper.HandshakeCallback() {
            @Override
            public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                callback.onConnectCompleted(e, socket);
            }
        };
    }

    protected void tryHandshake(AsyncSocket socket, GetSocketData data, final Uri uri, final int port, final ConnectCallback callback) {
        AsyncSSLSocketWrapper.handshake(socket, uri.getHost(), port,
        createConfiguredSSLEngine(data, uri.getHost(), port),
        trustManagers, hostnameVerifier, true,
        createHandshakeCallback(data, callback));
    }

    @Override
    protected ConnectCallback wrapCallback(final GetSocketData data, final Uri uri, final int port, final boolean proxied, final ConnectCallback callback) {
        return new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, final AsyncSocket socket) {
                if (ex != null) {
                    callback.onConnectCompleted(ex, socket);
                    return;
                }

                if (!proxied) {
                    tryHandshake(socket, data, uri, port, callback);
                    return;
                }

                // this SSL connection is proxied, must issue a CONNECT request to the proxy server
                // http://stackoverflow.com/a/6594880/704837
                String connect = String.format("CONNECT %s:%s HTTP/1.1\r\n\r\n", uri.getHost(), port);
                Util.writeAll(socket, connect.getBytes(), new CompletedCallback() {
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

                                    RawHeaders headers = new RawHeaders();
                                    headers.setStatusLine(statusLine);
                                    int code = headers.getResponseCode();

                                    if (statusLine.length() > 128 || code < 200 || code > 299) {
                                        socket.setDataCallback(null);
                                        socket.setEndCallback(null);
                                        callback.onConnectCompleted(new IOException("non 200 status line: " + statusLine), socket);
                                    }
                                }
                                else {
                                    socket.setDataCallback(null);
                                    socket.setEndCallback(null);
                                    if (TextUtils.isEmpty(s.trim())) {
                                        tryHandshake(socket, data, uri, port, callback);
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
                    }
                });
            }
        };
    }
}
