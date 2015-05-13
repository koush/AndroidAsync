package com.koushikdutta.async.http.spdy;

import android.net.Uri;
import android.text.TextUtils;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.MultiFuture;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncSSLEngineConfigurator;
import com.koushikdutta.async.http.AsyncSSLSocketMiddleware;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.util.Charsets;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class SpdyMiddleware extends AsyncSSLSocketMiddleware {
    public SpdyMiddleware(AsyncHttpClient client) {
        super(client);
        addEngineConfigurator(new AsyncSSLEngineConfigurator() {
            @Override
            public void configureEngine(SSLEngine engine, GetSocketData data, String host, int port) {
                configure(engine, data, host, port);
            }
        });
    }

    private void configure(SSLEngine engine, GetSocketData data, String host, int port) {
        if (!initialized && spdyEnabled) {
            initialized = true;
            try {
                peerHost = engine.getClass().getSuperclass().getDeclaredField("peerHost");
                peerPort = engine.getClass().getSuperclass().getDeclaredField("peerPort");
                sslParameters = engine.getClass().getDeclaredField("sslParameters");
                npnProtocols = sslParameters.getType().getDeclaredField("npnProtocols");
                alpnProtocols = sslParameters.getType().getDeclaredField("alpnProtocols");
                useSni = sslParameters.getType().getDeclaredField("useSni");
                sslNativePointer = engine.getClass().getDeclaredField("sslNativePointer");
                String nativeCryptoName = sslParameters.getType().getPackage().getName() + ".NativeCrypto";
                nativeGetNpnNegotiatedProtocol = Class.forName(nativeCryptoName, true, sslParameters.getType().getClassLoader())
                .getDeclaredMethod("SSL_get_npn_negotiated_protocol", long.class);
                nativeGetAlpnNegotiatedProtocol = Class.forName(nativeCryptoName, true, sslParameters.getType().getClassLoader())
                .getDeclaredMethod("SSL_get0_alpn_selected", long.class);

                peerHost.setAccessible(true);
                peerPort.setAccessible(true);
                sslParameters.setAccessible(true);
                npnProtocols.setAccessible(true);
                alpnProtocols.setAccessible(true);
                useSni.setAccessible(true);
                sslNativePointer.setAccessible(true);
                nativeGetNpnNegotiatedProtocol.setAccessible(true);
                nativeGetAlpnNegotiatedProtocol.setAccessible(true);
            }
            catch (Exception e) {
                sslParameters = null;
                npnProtocols = null;
                alpnProtocols = null;
                useSni = null;
                sslNativePointer = null;
                nativeGetNpnNegotiatedProtocol = null;
                nativeGetAlpnNegotiatedProtocol = null;
            }
        }

        // TODO: figure out why POST does not work if sending content-length header
        // see above regarding app engine comment as to why: drive requires content-length
        // but app engine sends a GO_AWAY if it sees a content-length...
        if (!canSpdyRequest(data))
            return;

        if (sslParameters != null) {
            try {
                byte[] protocols = concatLengthPrefixed(
                Protocol.SPDY_3
                );

                peerHost.set(engine, host);
                peerPort.set(engine, port);
                Object sslp = sslParameters.get(engine);
//                npnProtocols.set(sslp, protocols);
                alpnProtocols.set(sslp, protocols);
                useSni.set(sslp, true);
            }
            catch (Exception e ) {
                e.printStackTrace();
            }
        }
    }

    boolean initialized;
    Field peerHost;
    Field peerPort;
    Field sslParameters;
    Field npnProtocols;
    Field alpnProtocols;
    Field sslNativePointer;
    Field useSni;
    Method nativeGetNpnNegotiatedProtocol;
    Method nativeGetAlpnNegotiatedProtocol;
    Hashtable<String, SpdyConnectionWaiter> connections = new Hashtable<String, SpdyConnectionWaiter>();
    boolean spdyEnabled;

    private static class SpdyConnectionWaiter extends MultiFuture<AsyncSpdyConnection> {
        SimpleCancellable originalCancellable = new SimpleCancellable();
    }

    public boolean getSpdyEnabled() {
        return spdyEnabled;
    }

    public void setSpdyEnabled(boolean enabled) {
        spdyEnabled = enabled;
    }

    @Override
    public void setSSLContext(SSLContext sslContext) {
        super.setSSLContext(sslContext);
        initialized = false;
    }

    static byte[] concatLengthPrefixed(Protocol... protocols) {
        ByteBuffer result = ByteBuffer.allocate(8192);
        for (Protocol protocol: protocols) {
            if (protocol == Protocol.HTTP_1_0) continue; // No HTTP/1.0 for NPN.
            result.put((byte) protocol.toString().length());
            result.put(protocol.toString().getBytes(Charsets.UTF_8));
        }
        result.flip();
        byte[] ret = new ByteBufferList(result).getAllByteArray();
        return ret;
    }

    private static String requestPath(Uri uri) {
        String pathAndQuery = uri.getEncodedPath();
        if (pathAndQuery == null)
            pathAndQuery = "/";
        else if (!pathAndQuery.startsWith("/"))
            pathAndQuery = "/" + pathAndQuery;
        if (!TextUtils.isEmpty(uri.getEncodedQuery()))
            pathAndQuery += "?" + uri.getEncodedQuery();
        return pathAndQuery;
    }

    private static class NoSpdyException extends Exception {
    }
    private static final NoSpdyException NO_SPDY = new NoSpdyException();

    private void noSpdy(String key) {
        SpdyConnectionWaiter conn = connections.remove(key);
        if (conn != null)
            conn.setComplete(NO_SPDY);
    }

    private void invokeConnect(String key, final ConnectCallback callback, Exception e, AsyncSSLSocket socket) {
        SpdyConnectionWaiter waiter = connections.get(key);
        if (waiter == null || waiter.originalCancellable.setComplete())
            callback.onConnectCompleted(e, socket);
    }

    @Override
    protected AsyncSSLSocketWrapper.HandshakeCallback createHandshakeCallback(final GetSocketData data, final ConnectCallback callback) {
        final String key = data.state.get("spdykey");
        if (key == null)
            return super.createHandshakeCallback(data, callback);

        return new AsyncSSLSocketWrapper.HandshakeCallback() {
            @Override
            public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                data.request.logv("checking spdy handshake");
                if (e != null || nativeGetAlpnNegotiatedProtocol == null) {
                    invokeConnect(key, callback, e, socket);
                    noSpdy(key);
                    return;
                }
                String protoString;
                try {
                    long ptr = (Long)sslNativePointer.get(socket.getSSLEngine());
                    byte[] proto = (byte[])nativeGetAlpnNegotiatedProtocol.invoke(null, ptr);
                    if (proto == null) {
                        invokeConnect(key, callback, null, socket);
                        noSpdy(key);
                        return;
                    }
                    protoString = new String(proto);
                    Protocol p = Protocol.get(protoString);
                    if (p == null || !p.needsSpdyConnection()) {
                        invokeConnect(key, callback, null, socket);
                        noSpdy(key);
                        return;
                    }
                }
                catch (Exception ex) {
                    throw new AssertionError(ex);
                }

                final AsyncSpdyConnection connection = new AsyncSpdyConnection(socket, Protocol.get(protoString)) {
                    boolean hasReceivedSettings;
                    @Override
                    public void settings(boolean clearPrevious, Settings settings) {
                        super.settings(clearPrevious, settings);
                        if (!hasReceivedSettings) {
                            try {
                                sendConnectionPreface();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            hasReceivedSettings = true;

                            SpdyConnectionWaiter waiter = connections.get(key);

                            if (waiter.originalCancellable.setComplete()) {
                                data.request.logv("using new spdy connection for host: " + data.request.getUri().getHost());
                                newSocket(data, this, callback);
                            }

                            waiter.setComplete(this);
                        }
                    }
                };
            }
        };
    }

    private void newSocket(GetSocketData data, final AsyncSpdyConnection connection, final ConnectCallback callback) {
        final AsyncHttpRequest request = data.request;

        data.protocol = connection.protocol.toString();

        final AsyncHttpRequestBody requestBody = data.request.getBody();

        // this causes app engine to shit a brick, but if it is missing,
        // drive shits the bed
//        if (requestBody != null) {
//            if (requestBody.length() >= 0) {
//                request.getHeaders().set("Content-Length", String.valueOf(requestBody.length()));
//            }
//        }

        final ArrayList<Header> headers = new ArrayList<Header>();
        headers.add(new Header(Header.TARGET_METHOD, request.getMethod()));
        headers.add(new Header(Header.TARGET_PATH, requestPath(request.getUri())));
        String host = request.getHeaders().get("Host");
        if (Protocol.SPDY_3 == connection.protocol) {
            headers.add(new Header(Header.VERSION, "HTTP/1.1"));
            headers.add(new Header(Header.TARGET_HOST, host));
        } else if (Protocol.HTTP_2 == connection.protocol) {
            headers.add(new Header(Header.TARGET_AUTHORITY, host)); // Optional in HTTP/2
        } else {
            throw new AssertionError();
        }
        headers.add(new Header(Header.TARGET_SCHEME, request.getUri().getScheme()));

        final Multimap mm = request.getHeaders().getMultiMap();
        for (String key: mm.keySet()) {
            if (SpdyTransport.isProhibitedHeader(connection.protocol, key))
                continue;
            for (String value: mm.get(key)) {
                headers.add(new Header(key.toLowerCase(), value));
            }
        }

        request.logv("\n" + request);
        final AsyncSpdyConnection.SpdySocket spdy = connection.newStream(headers, requestBody != null, true);
        callback.onConnectCompleted(null, spdy);
    }

    private boolean canSpdyRequest(GetSocketData data) {
        // TODO: figure out why POST does not work if sending content-length header
        // see above regarding app engine comment as to why: drive requires content-length
        // but app engine sends a GO_AWAY if it sees a content-length...
        return data.request.getBody() == null;
    }

    @Override
    protected ConnectCallback wrapCallback(final GetSocketData data, final Uri uri, final int port, final boolean proxied, ConnectCallback callback) {
        final ConnectCallback superCallback = super.wrapCallback(data, uri, port, proxied, callback);
        final String key = data.state.get("spdykey");
        if (key == null)
            return superCallback;

        // new outgoing connection, try to make this a spdy connection
        return new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                // an exception here is an ssl or network exception... don't rule spdy out yet, but
                // trigger the waiters
                if (ex != null) {
                    final SpdyConnectionWaiter conn = connections.remove(key);
                    if (conn != null)
                        conn.setComplete(ex);
                }
                superCallback.onConnectCompleted(ex, socket);
            }
        };
    }

    @Override
    public Cancellable getSocket(final GetSocketData data) {
        final Uri uri = data.request.getUri();
        final int port = getSchemePort(data.request.getUri());
        if (port == -1) {
            return null;
        }

        if (!spdyEnabled)
            return super.getSocket(data);

        // TODO: figure out why POST does not work if sending content-length header
        // see above regarding app engine comment as to why: drive requires content-length
        // but app engine sends a GO_AWAY if it sees a content-length...
        if (!canSpdyRequest(data))
            return super.getSocket(data);

        // can we use an existing connection to satisfy this, or do we need a new one?
        String key = uri.getHost() + port;
        SpdyConnectionWaiter conn = connections.get(key);
        if (conn != null) {
            if (conn.tryGetException() instanceof NoSpdyException)
                return super.getSocket(data);

            // dead connection check
            if (conn.tryGet() != null && !conn.tryGet().socket.isOpen()) {
                // old spdy connection is derped, kill it with fire.
                connections.remove(key);
                conn = null;
            }
        }

        if (conn == null) {
            // no connection has ever been attempted (or previous one had a network death), so attempt one
            data.state.put("spdykey", key);
            // if we got something back synchronously, it's a keep alive socket
            Cancellable ret = super.getSocket(data);
            if (ret.isDone() || ret.isCancelled())
                return ret;
            conn = new SpdyConnectionWaiter();
            connections.put(key, conn);
            return conn.originalCancellable;
        }

        data.request.logv("waiting for potential spdy connection for host: " + data.request.getUri().getHost());
        final SimpleCancellable ret = new SimpleCancellable();
        conn.setCallback(new FutureCallback<AsyncSpdyConnection>() {
            @Override
            public void onCompleted(Exception e, AsyncSpdyConnection conn) {
                if (e instanceof NoSpdyException) {
                    data.request.logv("spdy not available");
                    ret.setParent(SpdyMiddleware.super.getSocket(data));
                    return;
                }
                if (e != null) {
                    if (ret.setComplete())
                        data.connectCallback.onConnectCompleted(e, null);
                    return;
                }
                data.request.logv("using existing spdy connection for host: " + data.request.getUri().getHost());
                if (ret.setComplete())
                    newSocket(data, conn, data.connectCallback);
            }
        });

        return ret;
    }

    @Override
    public boolean exchangeHeaders(final OnExchangeHeaderData data) {
        if (!(data.socket instanceof AsyncSpdyConnection.SpdySocket))
            return super.exchangeHeaders(data);

        AsyncHttpRequestBody requestBody = data.request.getBody();
        if (requestBody != null) {
            data.response.sink(data.socket);
        }

        // headers were already sent as part of the socket being opened.
        data.sendHeadersCallback.onCompleted(null);

        final AsyncSpdyConnection.SpdySocket spdySocket = (AsyncSpdyConnection.SpdySocket)data.socket;
        spdySocket.headers()
        .then(new TransformFuture<Headers, List<Header>>() {
            @Override
            protected void transform(List<Header> result) throws Exception {
                Headers headers = new Headers();
                for (Header header: result) {
                    String key = header.name.utf8();
                    String value = header.value.utf8();
                    headers.add(key, value);
                }
                String status = headers.remove(Header.RESPONSE_STATUS.utf8());
                String[] statusParts = status.split(" ", 2);
                data.response.code(Integer.parseInt(statusParts[0]));
                if (statusParts.length == 2)
                    data.response.message(statusParts[1]);
                data.response.protocol(headers.remove(Header.VERSION.utf8()));
                data.response.headers(headers);
                setComplete(headers);
            }
        })
        .setCallback(new FutureCallback<Headers>() {
            @Override
            public void onCompleted(Exception e, Headers result) {
                data.receiveHeadersCallback.onCompleted(e);
                DataEmitter emitter = HttpUtil.getBodyDecoder(spdySocket, spdySocket.getConnection().protocol, result, false);
                data.response.emitter(emitter);
            }
        });
        return true;
    }

    @Override
    public void onRequestSent(OnRequestSentData data) {
        if (!(data.socket instanceof AsyncSpdyConnection.SpdySocket))
            return;

        if (data.request.getBody() != null)
            data.response.sink().end();
    }
}