package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ContinuationCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Continuation;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.future.TransformFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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

    boolean connectAllAddresses;
    public boolean getConnectAllAddresses() {
        return connectAllAddresses;
    }

    public void setConnectAllAddresses(boolean connectAllAddresses) {
        this.connectAllAddresses = connectAllAddresses;
    }

    String proxyHost;
    int proxyPort;
    InetSocketAddress proxyAddress;

    public void disableProxy() {
        proxyPort = -1;
        proxyHost = null;
        proxyAddress = null;
    }

    public void enableProxy(String host, int port) {
        proxyHost = host;
        proxyPort = port;
        proxyAddress = null;
    }

    String computeLookup(URI uri, int port, AsyncHttpRequest request) {
        String proxy;
        if (proxyHost != null)
            proxy = proxyHost + ":" + proxyPort;
        else
            proxy = "";

        if (request.proxyHost != null)
            proxy = request.getProxyHost() + ":" + request.proxyPort;

        return uri.getScheme() + "//" + uri.getHost() + ":" + port + "?proxy=" + proxy;
    }

    @Override
    public Cancellable getSocket(final GetSocketData data) {
        final URI uri = data.request.getUri();
        final int port = getSchemePort(data.request.getUri());
        if (port == -1) {
            return null;
        }

        final String lookup = computeLookup(uri, port, data.request);
        
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

        if (!connectAllAddresses || proxyHost != null || data.request.getProxyHost() != null) {
            // just default to connecting to a single address
            data.request.logd("Connecting socket");
            String unresolvedHost;
            int unresolvedPort;
            if (data.request.getProxyHost() != null) {
                unresolvedHost = data.request.getProxyHost();
                unresolvedPort = data.request.getProxyPort();
                // set the host and port explicitly for proxied connections
                data.request.getHeaders().getHeaders().setStatusLine(data.request.getProxyRequestLine().toString());
            }
            else if (proxyHost != null) {
                unresolvedHost = proxyHost;
                unresolvedPort = proxyPort;
                // set the host and port explicitly for proxied connections
                data.request.getHeaders().getHeaders().setStatusLine(data.request.getProxyRequestLine().toString());
            }
            else {
                unresolvedHost = uri.getHost();
                unresolvedPort = port;
            }
            return mClient.getServer().connectSocket(unresolvedHost, unresolvedPort, wrapCallback(data.connectCallback, uri, port));
        }

        // try to connect to everything...
        data.request.logv("Resolving domain and connecting to all available addresses");
        return new TransformFuture<AsyncSocket, InetAddress[]>() {
            Exception lastException;

            @Override
            protected void error(Exception e) {
                super.error(e);
                data.connectCallback.onConnectCompleted(e, null);
            }

            @Override
            protected void transform(final InetAddress[] result) throws Exception {
                Continuation keepTrying = new Continuation(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        // if it completed, that means that the connection failed
                        if (lastException == null)
                            lastException = new Exception("Unable to connect to remote address");
                        setComplete(lastException);
                    }
                });

                for (final InetAddress address: result) {
                    keepTrying.add(new ContinuationCallback() {
                        @Override
                        public void onContinue(Continuation continuation, final CompletedCallback next) throws Exception {
                            mClient.getServer().connectSocket(new InetSocketAddress(address, port), wrapCallback(new ConnectCallback() {
                                @Override
                                public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                                    assert !isDone();

                                    // try the next address
                                    if (ex != null) {
                                        lastException = ex;
                                        next.onCompleted(null);
                                        return;
                                    }

                                    // if the socket is no longer needed, just hang onto it...
                                    if (isDone() || isCancelled()) {
                                        data.request.logd("Recycling extra socket leftover from cancelled operation");
                                        idleSocket(socket);
                                        recycleSocket(socket, data.request);
                                        return;
                                    }

                                    if (setComplete(null, socket)) {
                                        data.connectCallback.onConnectCompleted(ex, socket);
                                    }
                                }
                            }, uri, port));
                        }
                    });
                }

                keepTrying.start();
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

    private void recycleSocket(final AsyncSocket socket, AsyncHttpRequest request) {
        if (socket == null)
            return;
        URI uri = request.getUri();
        int port = getSchemePort(uri);
        String lookup = computeLookup(uri, port, request);
        // nothing here will block...
        synchronized (this) {
            HashSet<AsyncSocket> sockets = mSockets.get(lookup);
            if (sockets == null) {
                sockets = new HashSet<AsyncSocket>();
                mSockets.put(lookup, sockets);
            }
            final HashSet<AsyncSocket> ss = sockets;
            sockets.add(socket);
            // should not get any data after this point...
            // if so, eat it and disconnect.
            socket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    synchronized (AsyncSocketMiddleware.this) {
                        ss.remove(socket);
                    }
                    socket.setClosedCallback(null);
                }
            });
        }
    }

    private void idleSocket(final AsyncSocket socket) {
        socket.setEndCallback(null);
        socket.setWriteableCallback(null);
        socket.setDataCallback(new NullDataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                super.onDataAvailable(emitter, bb);
                bb.recycle();
                socket.close();
            }
        });
    }

    @Override
    public void onRequestComplete(final OnRequestCompleteData data) {
        if (!data.state.getBoolean(getClass().getCanonicalName() + ".owned", false)) {
            return;
        }

        idleSocket(data.socket);

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
        recycleSocket(data.socket, data.request);
    }
}
