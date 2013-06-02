package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.future.TransformFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
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

    boolean connectAllAddresses;
    public boolean getConnectAllAddresses() {
        return connectAllAddresses;
    }

    public void setConnectAllAddresses(boolean connectAllAddresses) {
        this.connectAllAddresses = connectAllAddresses;
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

        synchronized (this) {
            final HashSet<AsyncSocket> sockets = mSockets.get(lookup);
            if (sockets != null) {
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
                        return new SimpleCancellable();
                    }
                }
            }
        }

        if (!connectAllAddresses) {
            // just default to connecting to a single address
            data.request.logd("Connecting socket");
            return mClient.getServer().connectSocket(uri.getHost(), port, wrapCallback(data.connectCallback, uri, port));
        }

        // try to connect to everything...
        data.request.logv("Resolving domain and connecting to all available addresses");
        return new TransformFuture<AsyncSocket, InetAddress[]>() {
            int reported;
            @Override
            protected void transform(final InetAddress[] result) throws Exception {
                for (InetAddress address: result) {
                    mClient.getServer().connectSocket(new InetSocketAddress(address, port), wrapCallback(new ConnectCallback() {
                        @Override
                        public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                            // note how many callbacks we received
                            reported++;

                            // check if the socket was already provided by a previous callback or request cancelled
                            if (isDone() || isCancelled()) {
                                data.request.logd("Recycling extra socket leftover from connecting to all addresses");
                                recycleSocket(socket, uri);
                                return;
                            }

                            // need a result at this point

                            // only report the final exception... 4 addresses may have been resolved
                            // but we only care to report if ALL of them resulted in errors
                            if (ex != null && reported < result.length)
                                return;

                            if (setComplete(ex, socket))
                                data.connectCallback.onConnectCompleted(ex, socket);
                        }
                    }, uri, port));
                }
            }
        }
        .from(mClient.getServer().getAllByName(uri.getHost()));
    }

    public int getConnectionPoolCount() {
        int ret = 0;
        synchronized (this) {
            for (HashSet<AsyncSocket> sockets: mSockets.values()) {
                ret += sockets.size();
            }
        }
        return ret;
    }

    private void recycleSocket(final AsyncSocket socket, URI uri) {
        if (socket == null)
            return;
        int port = getSchemePort(uri);
        String lookup = uri.getScheme() + "//" + uri.getHost() + ":" + port;
        // nothing here will block...
        synchronized (this) {
            HashSet<AsyncSocket> sockets = mSockets.get(lookup);
            if (sockets == null) {
                sockets = new HashSet<AsyncSocket>();
                mSockets.put(lookup, sockets);
            }
            final HashSet<AsyncSocket> ss = sockets;
            sockets.add(socket);
            socket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    synchronized (this) {
                        ss.remove(socket);
                    }
                    socket.setClosedCallback(null);
                }
            });
        }
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
        recycleSocket(data.socket, data.request.getUri());
    }
}
