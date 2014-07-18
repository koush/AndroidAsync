package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncSSLEngineConfigurator;
import com.koushikdutta.async.http.AsyncSSLSocketMiddleware;
import com.koushikdutta.async.http.spdy.okhttp.Protocol;
import com.koushikdutta.async.util.Charsets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

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

    @Override
    protected AsyncSSLSocketWrapper.HandshakeCallback createHandshakeCallback(final ConnectCallback callback) {
        return new AsyncSSLSocketWrapper.HandshakeCallback() {
            @Override
            public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                if (e != null || nativeGetAlpnNegotiatedProtocol == null) {
                    callback.onConnectCompleted(e, socket);
                    return;
                }
                try {
                    long ptr = (long)sslNativePointer.get(socket.getSSLEngine());
                    byte[] proto = (byte[])nativeGetAlpnNegotiatedProtocol.invoke(null, ptr);
                    String protoString = new String(proto);
                    AsyncSpdyConnection connection = new AsyncSpdyConnection(socket, Protocol.get(protoString));
                }
                catch (Exception ex) {
                    socket.close();
                    callback.onConnectCompleted(ex, null);
                }
            }
        };
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

    @Override
    public void setSSLContext(SSLContext sslContext) {
        super.setSSLContext(sslContext);
        initialized = false;
    }

    @Override
    public Cancellable getSocket(GetSocketData data) {
        return super.getSocket(data);
    }
}