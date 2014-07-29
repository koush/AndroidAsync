package com.koushikdutta.async.http.spdy;

import android.net.Uri;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncSSLEngineConfigurator;
import com.koushikdutta.async.http.AsyncSSLSocketMiddleware;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.util.Charsets;

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
            public void configureEngine(SSLEngine engine, String host, int port) {
                configure(engine, host, port);
            }
        });
    }

    private void configure(SSLEngine engine, String host, int port) {
        if (!initialized) {
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

        if (sslParameters != null) {
            try {
                byte[] protocols = concatLengthPrefixed(
                Protocol.HTTP_1_1,
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

    @Override
    protected SSLEngine createConfiguredSSLEngine(String host, int port) {
        SSLContext sslContext = getSSLContext();
        SSLEngine sslEngine = sslContext.createSSLEngine();

        for (AsyncSSLEngineConfigurator configurator : engineConfigurators) {
            configurator.configureEngine(sslEngine, host, port);
        }

        return sslEngine;
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
    Hashtable<String, AsyncSpdyConnection> connections = new Hashtable<String, AsyncSpdyConnection>();

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
        String pathAndQuery = uri.getPath();
        if (pathAndQuery == null) return "/";
        if (!pathAndQuery.startsWith("/")) return "/" + pathAndQuery;
        return pathAndQuery;
    }

    @Override
    protected AsyncSSLSocketWrapper.HandshakeCallback createHandshakeCallback(final GetSocketData data, final ConnectCallback callback) {
        return new AsyncSSLSocketWrapper.HandshakeCallback() {
            @Override
            public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                if (e != null || nativeGetAlpnNegotiatedProtocol == null) {
                    callback.onConnectCompleted(e, socket);
                    return;
                }
                try {
                    long ptr = (Long)sslNativePointer.get(socket.getSSLEngine());
                    byte[] proto = (byte[])nativeGetAlpnNegotiatedProtocol.invoke(null, ptr);
                    if (proto == null) {
                        callback.onConnectCompleted(null, socket);
                        return;
                    }
                    String protoString = new String(proto);
                    Protocol p = Protocol.get(protoString);
                    if (p == null) {
                        callback.onConnectCompleted(null, socket);
                        return;
                    }
                    data.protocol = protoString;
                    final AsyncSpdyConnection connection = new AsyncSpdyConnection(socket, Protocol.get(protoString));
                    connection.sendConnectionPreface();

                    connections.put(data.request.getUri().getHost(), connection);

                    newSocket(data, connection, callback);
                }
                catch (Exception ex) {
                    socket.close();
                    callback.onConnectCompleted(ex, null);
                }
            }
        };
    }

    private void newSocket(GetSocketData data, final AsyncSpdyConnection connection, final ConnectCallback callback) {
        data.request.logv("using spdy connection");
        final ArrayList<Header> headers = new ArrayList<Header>();
        headers.add(new Header(Header.TARGET_METHOD, data.request.getMethod()));
        headers.add(new Header(Header.TARGET_PATH, requestPath(data.request.getUri())));
        String host = data.request.getHeaders().get("Host");
        if (Protocol.SPDY_3 == connection.protocol) {
            headers.add(new Header(Header.VERSION, "HTTP/1.1"));
            headers.add(new Header(Header.TARGET_HOST, host));
        } else if (Protocol.HTTP_2 == connection.protocol) {
            headers.add(new Header(Header.TARGET_AUTHORITY, host)); // Optional in HTTP/2
        } else {
            throw new AssertionError();
        }
        headers.add(new Header(Header.TARGET_SCHEME, data.request.getUri().getScheme()));

        Multimap mm = data.request.getHeaders().getMultiMap();
        for (String key: mm.keySet()) {
            if (SpdyTransport.isProhibitedHeader(connection.protocol, key))
                continue;
            for (String value: mm.get(key)) {
                headers.add(new Header(key.toLowerCase(), value));
            }
        }

        data.request.logv("\n" + data.request);
        AsyncSpdyConnection.SpdySocket spdy = connection.newStream(headers, data.request.getBody() != null, true);
        callback.onConnectCompleted(null, spdy);
    }

    @Override
    public Cancellable getSocket(GetSocketData data) {
        final Uri uri = data.request.getUri();
        final int port = getSchemePort(data.request.getUri());
        if (port == -1) {
            return null;
        }

        // can we use an existing connection to satisfy this, or do we need a new one?
        String host = uri.getHost();
        AsyncSpdyConnection conn = connections.get(host);
        if (conn == null || !conn.socket.isOpen()) {
            connections.remove(host);
            return super.getSocket(data);
        }

        newSocket(data, conn, data.connectCallback);

        SimpleCancellable ret = new SimpleCancellable();
        ret.setComplete();
        return ret;
    }

    @Override
    public boolean exchangeHeaders(final OnExchangeHeaderData data) {
        if (!(data.socket instanceof AsyncSpdyConnection.SpdySocket))
            return false;

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
                data.response.emitter(spdySocket);
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