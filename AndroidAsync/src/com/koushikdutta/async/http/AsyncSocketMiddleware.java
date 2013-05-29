package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.SimpleCancelable;

import java.net.URI;
import java.util.HashSet;
import java.util.Hashtable;

public class AsyncSocketMiddleware extends SimpleMiddleware {
    String scheme;
    int port;
    public AsyncSocketMiddleware(AsyncHttpClient client, String scheme, int port) {
        mClient = client;
        this.scheme = scheme;
        this.port = port;
    }
    
    public int getSchemePort(URI uri) {
        if (!uri.getScheme().equals(scheme))
            return -1;
        if (uri.getPort() == -1) {
            return port;
        }
        else {
            return uri.getPort();
        }
    }

    public AsyncSocketMiddleware(AsyncHttpClient client) {
        this(client, "http", 80);
    }

    AsyncHttpClient mClient;
    private Hashtable<String, HashSet<AsyncSocket>> mSockets = new Hashtable<String, HashSet<AsyncSocket>>();

    protected ConnectCallback wrapCallback(ConnectCallback callback, URI uri, int port) {
        return callback;
    }

    @Override
    public Cancellable getSocket(final GetSocketData data) {
        final URI uri = data.request.getUri();
        final int port = getSchemePort(data.request.getUri());
        if (port == -1) {
            return null;
        }
        final String lookup = uri.getScheme() + "//" + uri.getHost() + ":" + port;
        
        data.state.putBoolean(getClass().getCanonicalName() + ".owned", true);

        HashSet<AsyncSocket> sockets = mSockets.get(lookup);
        if (sockets != null) {
            synchronized (sockets) {
                for (final AsyncSocket socket: sockets) {
                    if (socket.isOpen()) {
                        sockets.remove(socket);
                        socket.setClosedCallback(null);
                        mClient.getServer().post(new Runnable() {
                            @Override
                            public void run() {
                                data.request.logd("Reusing keep-alive socket");
                                data.connectCallback.onConnectCompleted(null, socket);
                            }
                        });
                        // just a noop/dummy, as this can't actually be cancelled.
                        return new SimpleCancelable();
                    }
                }
            }
        }

        data.request.logd("Connecting socket");
        return mClient.getServer().connectSocket(uri.getHost(), port, wrapCallback(data.connectCallback, uri, port));
    }

    @Override
    public void onRequestComplete(final OnRequestCompleteData data) {
        if (!data.state.getBoolean(getClass().getCanonicalName() + ".owned", false)) {
            return;
        }

        if (data.exception != null || !data.socket.isOpen()) {
            data.socket.close();
            return;
        }
        String kas = data.headers.getConnection();
        if (kas == null || !"keep-alive".toLowerCase().equals(kas.toLowerCase())) {
            data.socket.close();
            return;
        }

        data.request.logd("Recycling keep-alive socket");

        final URI uri = data.request.getUri();
        final int port = getSchemePort(data.request.getUri());
        final String lookup = uri.getScheme() + "//" + uri.getHost() + ":" + port;
        HashSet<AsyncSocket> sockets = mSockets.get(lookup);
        if (sockets == null) {
            sockets = new HashSet<AsyncSocket>();
            mSockets.put(lookup, sockets);
        }
        final HashSet<AsyncSocket> ss = sockets;
        synchronized (sockets) {
            sockets.add(data.socket);
            data.socket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    synchronized (ss) {
                        ss.remove(data.socket);
                    }
                    data.socket.setClosedCallback(null);
                }
            });
        }
    }
}
