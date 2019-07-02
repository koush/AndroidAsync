/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.koushikdutta.async.test;


import android.content.Context;
import androidx.test.runner.AndroidJUnit4;

import org.junit.runner.RunWith;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;

/**
 * Created by koush on 7/15/14.
 */
@RunWith(AndroidJUnit4.class)
public class ConscryptTests {
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

    private void configure(SSLEngine engine, String host, int port) throws Exception {
        if (!initialized) {
            initialized = true;
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

        byte[] protocols = concatLengthPrefixed(
        "http/1.1",
        "spdy/3.1"
        );

        peerHost.set(engine, host);
        peerPort.set(engine, port);
        Object sslp = sslParameters.get(engine);
//        npnProtocols.set(sslp, protocols);
        alpnProtocols.set(sslp, protocols);
        useSni.set(sslp, true);
    }

    static byte[] concatLengthPrefixed(String... protocols) {
        ByteBuffer result = ByteBuffer.allocate(8192);
        for (String protocol: protocols) {
            result.put((byte) protocol.toString().length());
            result.put(protocol.toString().getBytes(Charset.forName("UTF-8")));
        }
        result.flip();
        byte[] ret = new byte[result.remaining()];
        result.get(ret);
        return ret;
    }

    public void testConscryptSSLEngineNPNHandshakeBug() throws Exception {
//        Security.insertProviderAt(new OpenSSLProvider("MyNameBlah"), 1);

        Context gms = getContext().createPackageContext("com.google.android.gms", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        gms
        .getClassLoader()
        .loadClass("com.google.android.gms.common.security.ProviderInstallerImpl")
        .getMethod("insertProvider", Context.class)
        .invoke(null, getContext());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, null, null);

        SSLEngine engine = ctx.createSSLEngine();
        configure(engine, "www.google.com", 443);
        engine.setUseClientMode(true);
        engine.beginHandshake();

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("www.google.com", 443));

        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        byte[] buf = new byte[65536];
        ByteBuffer unwrap = null;
        ByteBuffer dummy = ByteBuffer.allocate(65536);

        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();

        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED
            && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                System.out.println("waiting for read... " + engine.getHandshakeStatus());
                int read = is.read(buf);
                System.out.println("read: " + read);
                if (read <= 0)
                    throw new Exception("closed!");

                if (unwrap != null) {
                    int bufLen = unwrap.remaining() + read;
                    ByteBuffer b = ByteBuffer.allocate(bufLen);
                    b.put(unwrap);
                    b.put(buf, 0, read);
                    b.flip();
                    unwrap = b;
                }
                else {
                    unwrap = ByteBuffer.wrap(buf, 0, read);
                }

                if (!unwrap.hasRemaining()) {
                    unwrap = null;
                }

                dummy.clear();
                SSLEngineResult res = engine.unwrap(unwrap, dummy);
                System.out.println("data remaining after unwrap: " + unwrap.remaining());
                handshakeStatus = res.getHandshakeStatus();
            }

            if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                dummy.clear();
                SSLEngineResult res = engine.wrap(ByteBuffer.allocate(0), dummy);
                handshakeStatus = res.getHandshakeStatus();
                dummy.flip();
                if (dummy.hasRemaining()) {
                    os.write(dummy.array(), 0, dummy.remaining());
                }
            }
            else if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                engine.getDelegatedTask().run();
            }
        }


        System.out.println("Done handshaking! Thank you come again.");
        long ptr = (Long)sslNativePointer.get(engine);
        byte[] proto = (byte[]) nativeGetAlpnNegotiatedProtocol.invoke(null, ptr);
//        byte[] proto = (byte[]) nativeGetNpnNegotiatedProtocol.invoke(null, ptr);
        String protoString = new String(proto);
        System.out.println("negotiated protocol was: " + protoString);
        assertEquals(protoString, "spdy/3.1");

        socket.close();
    }
}
