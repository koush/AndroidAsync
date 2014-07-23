package com.koushikdutta.async.test;


import android.test.AndroidTestCase;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.spdy.okhttp.Handshake;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.util.Charsets;

import org.conscrypt.OpenSSLProvider;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Security;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public class OkHttpTest extends AndroidTestCase {
    public void testOkHttp() throws Exception {
//        Context context = getContext().getApplicationContext();
//        Context gms = context.createPackageContext("com.google.android.gms", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
//        gms
//        .getClassLoader()
//        .loadClass("com.google.android.gms.common.security.ProviderInstallerImpl")
//        .getMethod("insertProvider", Context.class)
//        .invoke(null, context);
        Security.insertProviderAt(new OpenSSLProvider("MyNameBlah"), 1);

        Class<?> openSslSocketClass;
        Method setUseSessionTickets;
        Method setHostname;
        openSslSocketClass = Class.forName("org.conscrypt.OpenSSLSocketImpl");
        setUseSessionTickets = openSslSocketClass.getMethod("setUseSessionTickets", boolean.class);
        setHostname = openSslSocketClass.getMethod("setHostname", String.class);
        Method trafficStatsTagSocket = null;
        Method trafficStatsUntagSocket = null;
        Class<?> trafficStats = Class.forName("android.net.TrafficStats");
        trafficStatsTagSocket = trafficStats.getMethod("tagSocket", Socket.class);
        trafficStatsUntagSocket = trafficStats.getMethod("untagSocket", Socket.class);

        // Attempt to find Android 4.1+ APIs.
        Method setNpnProtocols = null;
        Method getNpnSelectedProtocol = null;
        setNpnProtocols = openSslSocketClass.getMethod("setNpnProtocols", byte[].class);
        getNpnSelectedProtocol = openSslSocketClass.getMethod("getNpnSelectedProtocol");


//            Platform p = Platform.get();

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, null, null);
        Socket socket = SocketFactory.getDefault().createSocket();
        socket.connect(new InetSocketAddress("www.google.com", 443));
        socket = ctx.getSocketFactory().createSocket(socket, "www.google.com", 443, true);
        SSLSocket sslSocket = (SSLSocket) socket;

        setUseSessionTickets.invoke(sslSocket, true);
        setHostname.invoke(sslSocket, "www.google.com");
        setNpnProtocols.invoke(sslSocket, new Object[] { concatLengthPrefixed(Protocol.HTTP_1_1, Protocol.SPDY_3) });


        sslSocket.startHandshake();
        Handshake handshake = Handshake.get(sslSocket.getSession());

        String proto = new String((byte[])getNpnSelectedProtocol.invoke(sslSocket));

//        InputStream is = sslSocket.getInputStream();
//        StreamUtility.eat(is);

        System.out.println(proto);
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

}