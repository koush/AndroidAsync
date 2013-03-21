package com.koushikdutta.async.http;

import java.net.URI;
import java.util.HashSet;
import java.util.Hashtable;

import android.os.Bundle;
import android.util.Log;

import com.koushikdutta.async.AsyncNetworkSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.Cancelable;
import com.koushikdutta.async.SimpleCancelable;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public class AsyncSocketMiddleware implements AsyncHttpClientMiddleware {
    protected AsyncSocketMiddleware(AsyncHttpClient client, String scheme, int defaultPort) {
        mClient = client;
        mScheme = scheme;
        mDefaultPort = defaultPort;
    }
    
    String mScheme;
    int mDefaultPort;
    
    public AsyncSocketMiddleware(AsyncHttpClient client) {
        this(client, "http", 80);
    }
    AsyncHttpClient mClient;
    private Hashtable<String, HashSet<AsyncSocket>> mSockets = new Hashtable<String, HashSet<AsyncSocket>>();

    protected ConnectCallback wrapCallback(ConnectCallback callback, URI uri, int port) {
        return callback;
    }

    @Override
    public Cancelable getSocket(Bundle state, AsyncHttpRequest request, final ConnectCallback callback) {
        final URI uri = request.getUri();
        final int port;
        if (uri.getPort() == -1) {
            if (!uri.getScheme().equals(mScheme))
                return null;
            port = mDefaultPort;
        }
        else {
            port = uri.getPort();
        }
        final String lookup = mScheme + "//" + uri.getHost() + ":" + port;
        state.putString("socket.lookup", lookup);
        state.putString("socket.owner", getClass().getCanonicalName());

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
//                                Log.i("AsyncHttpSocket", "Reusing socket.");
                                callback.onConnectCompleted(null, socket);
                            }
                        });
                        // just a noop/dummy, as this can't actually be cancelled.
                        return new SimpleCancelable();
                    }
                }
            }
        }
        
        ConnectCallback connectCallback = wrapCallback(callback, uri, port);
        return mClient.getServer().connectSocket(uri.getHost(), port, connectCallback);
    }

    @Override
    public AsyncSocket onSocket(Bundle state, AsyncSocket socket, AsyncHttpRequest request) {
        return socket;
    }

    @Override
    public void onHeadersReceived(Bundle state, AsyncSocket socket, AsyncHttpRequest request, ResponseHeaders headers) {
    }

    @Override
    public void onRequestComplete(Bundle state, final AsyncSocket socket, AsyncHttpRequest request, ResponseHeaders headers, Exception ex) {
        if (!getClass().getCanonicalName().equals(state.getString("socket.owner"))) {
//            Log.i("AsyncHttpSocket", getClass().getCanonicalName() + " Not keeping non-owned socket: " + state.getString("socket.owner"));
            return;
        }
        if (ex != null || !socket.isOpen()) {
            socket.close();
            return;
        }
        String kas = headers.getConnection();
        if (kas != null && "keep-alive".toLowerCase().equals(kas.toLowerCase())) {
            String lookup = state.getString("socket.lookup");
            if (lookup == null)
                return;
            HashSet<AsyncSocket> sockets = mSockets.get(lookup);
            if (sockets == null) {
                sockets = new HashSet<AsyncSocket>();
                mSockets.put(lookup, sockets);
            }
            final HashSet<AsyncSocket> ss = sockets;
            synchronized (sockets) {
                sockets.add(socket);
                socket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        synchronized (ss) {
                            ss.remove(socket);
                        }
                        socket.setClosedCallback(null);
                    }
                });
            }
        }
        else {
            socket.close();
        }
    }
}
