package com.koushikdutta.async;

import android.content.Context;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.http.SSLEngineSNIConfigurator;
import com.koushikdutta.async.util.Allocator;
import com.koushikdutta.async.util.StreamUtility;
import com.koushikdutta.async.wrapper.AsyncSocketWrapper;

import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class AsyncSSLSocketWrapper implements AsyncSocketWrapper, AsyncSSLSocket {
    private static final String LOGTAG = "AsyncSSLSocketWrapper";

    public interface HandshakeCallback {
        public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket);
    }

    static SSLContext defaultSSLContext;
    static SSLContext trustAllSSLContext;
    static TrustManager[] trustAllManagers;
    static HostnameVerifier trustAllVerifier;

    AsyncSocket mSocket;
    BufferedDataSink mSink;
    boolean mUnwrapping;
    SSLEngine engine;
    boolean finishedHandshake;
    private int mPort;
    private String mHost;
    private boolean mWrapping;
    HostnameVerifier hostnameVerifier;
    HandshakeCallback handshakeCallback;
    X509Certificate[] peerCertificates;
    WritableCallback mWriteableCallback;
    DataCallback mDataCallback;
    TrustManager[] trustManagers;
    boolean clientMode;

    static {
        // following is the "trust the system" certs setup
        try {
            // critical extension 2.5.29.15 is implemented improperly prior to 4.0.3.
            // https://code.google.com/p/android/issues/detail?id=9307
            // https://groups.google.com/forum/?fromgroups=#!topic/netty/UCfqPPk5O4s
            // certs that use this extension will throw in Cipher.java.
            // fallback is to use a custom SSLContext, and hack around the x509 extension.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                throw new Exception();
            defaultSSLContext = SSLContext.getInstance("Default");
        }
        catch (Exception ex) {
            try {
                defaultSSLContext = SSLContext.getInstance("TLS");
                TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        for (X509Certificate cert : certs) {
                            if (cert != null && cert.getCriticalExtensionOIDs() != null)
                                cert.getCriticalExtensionOIDs().remove("2.5.29.15");
                        }
                    }
                } };
                defaultSSLContext.init(null, trustAllCerts, null);
            }
            catch (Exception ex2) {
                ex.printStackTrace();
                ex2.printStackTrace();
            }
        }


        try {
            trustAllSSLContext = SSLContext.getInstance("TLS");
            trustAllManagers = new TrustManager[] { new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }
            } };
            trustAllSSLContext.init(null, trustAllManagers, null);
            trustAllVerifier = (hostname, session) -> true;
        }
        catch (Exception ex2) {
            ex2.printStackTrace();
        }
    }

    public static SSLContext getDefaultSSLContext() {
        return defaultSSLContext;
    }

    public static void handshake(AsyncSocket socket,
                                 String host, int port,
                                 SSLEngine sslEngine,
                                 TrustManager[] trustManagers, HostnameVerifier verifier, boolean clientMode,
                                 final HandshakeCallback callback) {
        AsyncSSLSocketWrapper wrapper = new AsyncSSLSocketWrapper(socket, host, port, sslEngine, trustManagers, verifier, clientMode);
        wrapper.handshakeCallback = callback;
        socket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null)
                    callback.onHandshakeCompleted(ex, null);
                else
                    callback.onHandshakeCompleted(new SSLException("socket closed during handshake"), null);
            }
        });
        try {
            wrapper.engine.beginHandshake();
            wrapper.handleHandshakeStatus(wrapper.engine.getHandshakeStatus());
        } catch (SSLException e) {
            wrapper.report(e);
        }
    }

    public static Cancellable connectSocket(AsyncServer server, String host, int port, ConnectCallback callback) {
        return connectSocket(server, host, port, false, callback);
    }
    public static Cancellable connectSocket(AsyncServer server, String host, int port, boolean trustAllCerts, ConnectCallback callback) {
        SimpleCancellable cancellable = new SimpleCancellable();
        Cancellable connect = server.connectSocket(host, port, (ex, netSocket) -> {
            if (ex != null) {
                if (cancellable.setComplete())
                    callback.onConnectCompleted(ex, null);
                return;
            }

            handshake(netSocket, host, port,
                    (trustAllCerts ? trustAllSSLContext : defaultSSLContext).createSSLEngine(host, port),
                    trustAllCerts ? trustAllManagers : null,
                    trustAllCerts ? trustAllVerifier : null,
                    true, (e, socket) -> {
                if (!cancellable.setComplete()) {
                    if (socket != null)
                        socket.close();
                    return;
                }

                if (e != null)
                    callback.onConnectCompleted(e, null);
                else
                    callback.onConnectCompleted(null, socket);
            });
        });

        cancellable.setParent(connect);
        return cancellable;
    }

    boolean mEnded;
    Exception mEndException;
    final ByteBufferList pending = new ByteBufferList();

    private AsyncSSLSocketWrapper(AsyncSocket socket,
                                  String host, int port,
                                  SSLEngine sslEngine,
                                  TrustManager[] trustManagers, HostnameVerifier verifier, boolean clientMode) {
        mSocket = socket;
        hostnameVerifier = verifier;
        this.clientMode = clientMode;
        this.trustManagers = trustManagers;
        this.engine = sslEngine;

        mHost = host;
        mPort = port;
        engine.setUseClientMode(clientMode);
        mSink = new BufferedDataSink(socket);
        mSink.setWriteableCallback(new WritableCallback() {
            @Override
            public void onWriteable() {
                if (mWriteableCallback != null)
                    mWriteableCallback.onWriteable();
            }
        });

        // on pause, the emitter is paused to prevent the buffered
        // socket and itself from firing.
        // on resume, emitter is resumed, ssl buffer is flushed as well
        mSocket.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (mEnded)
                    return;
                mEnded = true;
                mEndException = ex;
                if (!pending.hasRemaining() && mEndCallback != null)
                    mEndCallback.onCompleted(ex);
            }
        });

        mSocket.setDataCallback(dataCallback);
    }

    final DataCallback dataCallback = new DataCallback() {
        final Allocator allocator = new Allocator().setMinAlloc(8192);
        final ByteBufferList buffered = new ByteBufferList();

        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            if (mUnwrapping)
                return;
            try {
                mUnwrapping = true;

                bb.get(buffered);

                if (buffered.hasRemaining()) {
                    ByteBuffer all = buffered.getAll();
                    buffered.add(all);
                }

                ByteBuffer b = ByteBufferList.EMPTY_BYTEBUFFER;
                while (true) {
                    if (b.remaining() == 0 && buffered.size() > 0) {
                        b = buffered.remove();
                    }
                    int remaining = b.remaining();
                    int before = pending.remaining();

                    SSLEngineResult res;
                    {
                        // wrap to prevent access to the readBuf
                        ByteBuffer readBuf = allocator.allocate();
                        res = engine.unwrap(b, readBuf);
                        addToPending(pending, readBuf);
                        allocator.track(pending.remaining() - before);
                    }
                    if (res.getStatus() == Status.BUFFER_OVERFLOW) {
                        allocator.setMinAlloc(allocator.getMinAlloc() * 2);
                        remaining = -1;
                    }
                    else if (res.getStatus() == Status.BUFFER_UNDERFLOW) {
                        buffered.addFirst(b);
                        if (buffered.size() <= 1) {
                            break;
                        }
                        // pack it
                        remaining = -1;
                        b = buffered.getAll();
                        buffered.addFirst(b);
                        b = ByteBufferList.EMPTY_BYTEBUFFER;
                    }
                    handleHandshakeStatus(res.getHandshakeStatus());
                    if (b.remaining() == remaining && before == pending.remaining()) {
                        buffered.addFirst(b);
                        break;
                    }
                }

                AsyncSSLSocketWrapper.this.onDataAvailable();
            }
            catch (SSLException ex) {
//                ex.printStackTrace();
                report(ex);
            }
            finally {
                mUnwrapping = false;
            }
        }
    };

    public void onDataAvailable() {
        Util.emitAllData(this, pending);

        if (mEnded && !pending.hasRemaining() && mEndCallback != null)
            mEndCallback.onCompleted(mEndException);
    }


    @Override
    public SSLEngine getSSLEngine() {
        return engine;
    }

    void addToPending(ByteBufferList out, ByteBuffer mReadTmp) {
        mReadTmp.flip();
        if (mReadTmp.hasRemaining()) {
            out.add(mReadTmp);
        }
        else {
            ByteBufferList.reclaim(mReadTmp);
        }
    }


    @Override
    public void end() {
        mSocket.end();
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    private void handleHandshakeStatus(HandshakeStatus status) {
        if (status == HandshakeStatus.NEED_TASK) {
            final Runnable task = engine.getDelegatedTask();
            task.run();
        }

        if (status == HandshakeStatus.NEED_WRAP) {
            write(writeList);
        }

        if (status == HandshakeStatus.NEED_UNWRAP) {
            dataCallback.onDataAvailable(this, new ByteBufferList());
        }

        try {
            if (!finishedHandshake && (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING || engine.getHandshakeStatus() == HandshakeStatus.FINISHED)) {
                if (clientMode) {
                    Exception peerUnverifiedCause = null;
                    boolean trusted = false;
                    try {
                        peerCertificates = (X509Certificate[]) engine.getSession().getPeerCertificates();
                        if (mHost != null) {
                            if (hostnameVerifier == null) {
                                StrictHostnameVerifier verifier = new StrictHostnameVerifier();
                                verifier.verify(mHost, StrictHostnameVerifier.getCNs(peerCertificates[0]), StrictHostnameVerifier.getDNSSubjectAlts(peerCertificates[0]));
                            }
                            else {
                                if (!hostnameVerifier.verify(mHost, engine.getSession())) {
                                    throw new SSLException("hostname <" + mHost + "> has been denied");
                                }
                            }
                        }

                        trusted = true;
                    }
                    catch (SSLException ex) {
                        peerUnverifiedCause = ex;
                    }

                    finishedHandshake = true;
                    if (!trusted) {
                        AsyncSSLException e = new AsyncSSLException(peerUnverifiedCause);
                        report(e);
                        if (!e.getIgnore())
                            throw e;
                    }
                }
                else {
                    finishedHandshake = true;
                }
                handshakeCallback.onHandshakeCompleted(null, this);
                handshakeCallback = null;

                mSocket.setClosedCallback(null);
                // handshake can complete during a wrap, so make sure that the call
                // stack and wrap flag is cleared before invoking writable
                getServer().post(new Runnable() {
                    @Override
                    public void run() {
                        if (mWriteableCallback != null)
                            mWriteableCallback.onWriteable();
                    }
                });
                onDataAvailable();
            }
        }
        catch (Exception ex) {
            report(ex);
        }
    }

    int calculateAlloc(int remaining) {
        // alloc 50% more than we need for writing
        int alloc = remaining * 3 / 2;
        if (alloc == 0)
            alloc = 8192;
        return alloc;
    }

    ByteBufferList writeList = new ByteBufferList();
    @Override
    public void write(ByteBufferList bb) {
        if (mWrapping)
            return;
        if (mSink.remaining() > 0)
            return;
        mWrapping = true;
        int remaining;
        SSLEngineResult res = null;
        ByteBuffer writeBuf = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
        do {
            // if the handshake is finished, don't send
            // 0 bytes of data, since that makes the ssl connection die.
            // it wraps a 0 byte package, and craps out.
            if (finishedHandshake && bb.remaining() == 0)
                break;
            remaining = bb.remaining();
            try {
                ByteBuffer[] arr = bb.getAllArray();
                res = engine.wrap(arr, writeBuf);
                bb.addAll(arr);
                writeBuf.flip();
                writeList.add(writeBuf);
                if (writeList.remaining() > 0)
                    mSink.write(writeList);
                int previousCapacity = writeBuf.capacity();
                writeBuf = null;
                if (res.getStatus() == Status.BUFFER_OVERFLOW) {
                    writeBuf = ByteBufferList.obtain(previousCapacity * 2);
                    remaining = -1;
                }
                else {
                    writeBuf = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
                    handleHandshakeStatus(res.getHandshakeStatus());
                }
            }
            catch (SSLException e) {
                report(e);
            }
        }
        while ((remaining != bb.remaining() || (res != null && res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP)) && mSink.remaining() == 0);
        mWrapping = false;
        ByteBufferList.reclaim(writeBuf);
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mWriteableCallback = handler;
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mWriteableCallback;
    }

    private void report(Exception e) {
        final HandshakeCallback hs = handshakeCallback;
        if (hs != null) {
            handshakeCallback = null;
            mSocket.setDataCallback(new DataCallback.NullDataCallback());
            mSocket.end();
            // handshake sets this callback. unset it.
            mSocket.setClosedCallback(null);
            mSocket.close();
            hs.onHandshakeCompleted(e, null);
            return;
        }

        CompletedCallback cb = getEndCallback();
        if (cb != null)
            cb.onCompleted(e);
    }

    @Override
    public void setDataCallback(DataCallback callback) {
        mDataCallback = callback;
    }

    @Override
    public DataCallback getDataCallback() {
        return mDataCallback;
    }

    @Override
    public boolean isChunked() {
        return mSocket.isChunked();
    }

    @Override
    public boolean isOpen() {
        return mSocket.isOpen();
    }

    @Override
    public void close() {
        mSocket.close();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mSocket.setClosedCallback(handler);
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mSocket.getClosedCallback();
    }

    CompletedCallback mEndCallback;
    @Override
    public void setEndCallback(CompletedCallback callback) {
        mEndCallback = callback;
    }

    @Override
    public CompletedCallback getEndCallback() {
        return mEndCallback;
    }

    @Override
    public void pause() {
        mSocket.pause();
    }

    @Override
    public void resume() {
        mSocket.resume();
        onDataAvailable();
    }

    @Override
    public boolean isPaused() {
        return mSocket.isPaused();
    }

    @Override
    public AsyncServer getServer() {
        return mSocket.getServer();
    }

    @Override
    public AsyncSocket getSocket() {
        return mSocket;
    }

    @Override
    public DataEmitter getDataEmitter() {
        return mSocket;
    }

    @Override
    public X509Certificate[] getPeerCertificates() {
        return peerCertificates;
    }

    @Override
    public String charset() {
        return null;
    }

    private static Certificate selfSign(KeyPair keyPair, String subjectDN) throws Exception
    {
        Provider bcProvider = new BouncyCastleProvider();
        Security.addProvider(bcProvider);

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        X500Name dnName = new X500Name("CN=" + subjectDN);
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // <-- Using the current timestamp as the certificate serial number

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 1); // <-- 1 Yr validity

        Date endDate = calendar.getTime();

        String signatureAlgorithm = "SHA256WithRSA"; // <-- Use appropriate signature algorithm based on your keyPair algorithm.

        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        // Extensions --------------------------

        // Basic Constraints
        BasicConstraints basicConstraints = new BasicConstraints(true); // <-- true for CA, false for EndEntity

        certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints); // Basic Constraints is usually marked as critical.

        // -------------------------------------

        return new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner));
    }

    public static Pair<KeyPair, Certificate> selfSignCertificate(final Context context, String subjectName) throws Exception {
        File keyPath = context.getFileStreamPath(subjectName + "-key.txt");
        KeyPair pair;
        Certificate cert;
        try {
            String[] keyParts = StreamUtility.readFile(keyPath).split("\n");
            X509EncodedKeySpec pub = new X509EncodedKeySpec(Base64.decode(keyParts[0], 0));
            PKCS8EncodedKeySpec priv = new PKCS8EncodedKeySpec(Base64.decode(keyParts[1], 0));

            cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.decode(keyParts[2], 0)));

            KeyFactory fact = KeyFactory.getInstance("RSA");

            pair = new KeyPair(fact.generatePublic(pub), fact.generatePrivate(priv));

        }
        catch (Exception e) {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            pair = keyGen.generateKeyPair();

            cert = selfSign(pair, subjectName);

            StreamUtility.writeFile(keyPath,
                    Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP)
                            + "\n"
                            + Base64.encodeToString(pair.getPrivate().getEncoded(), Base64.NO_WRAP)
                            + "\n"
                            + Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP));
        }

        return new Pair<>(pair, cert);
    }

    public static AsyncSSLServerSocket listenSecure(final Context context, final AsyncServer server, final String subjectName, final InetAddress host, final int port, final ListenCallback handler) {
        final ObjectHolder<AsyncSSLServerSocket> holder = new ObjectHolder<>();
        server.run(() -> {
            try {
                Pair<KeyPair, Certificate> keyCert = selfSignCertificate(context, subjectName);
                KeyPair pair = keyCert.first;
                Certificate cert = keyCert.second;

                holder.held = listenSecure(server, pair.getPrivate(), cert, host, port, handler);
            }
            catch (Exception e) {
                handler.onCompleted(e);
            }
        });
        return holder.held;
    }

    public static AsyncSSLServerSocket listenSecure(AsyncServer server, String keyDer, String certDer, final InetAddress host, final int port, final ListenCallback handler) {
        return listenSecure(server, Base64.decode(keyDer, Base64.DEFAULT), Base64.decode(certDer, Base64.DEFAULT), host, port, handler);
    }

    private static class ObjectHolder<T> {
        T held;
    }

    public static AsyncSSLServerSocket listenSecure(final AsyncServer server, final byte[] keyDer, final byte[] certDer, final InetAddress host, final int port, final ListenCallback handler) {
        final ObjectHolder<AsyncSSLServerSocket> holder = new ObjectHolder<>();
        server.run(() -> {
            try {
                PKCS8EncodedKeySpec key = new PKCS8EncodedKeySpec(keyDer);
                Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certDer));

                PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(key);

                holder.held = listenSecure(server, pk, cert, host, port, handler);
            }
            catch (Exception e) {
                handler.onCompleted(e);
            }
        });
        return holder.held;
    }

    public static AsyncSSLServerSocket listenSecure(final AsyncServer server, final PrivateKey pk, final Certificate cert, final InetAddress host, final int port, final ListenCallback handler) {
        final ObjectHolder<AsyncSSLServerSocket> holder = new ObjectHolder<>();
        server.run(() -> {
            try {
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null);

                ks.setKeyEntry("key", pk, null, new Certificate[] { cert });

                KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
                kmf.init(ks, "".toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                final AsyncServerSocket socket = listenSecure(server, sslContext, host, port, handler);
                holder.held = new AsyncSSLServerSocket() {
                    @Override
                    public PrivateKey getPrivateKey() {
                        return pk;
                    }

                    @Override
                    public Certificate getCertificate() {
                        return cert;
                    }

                    @Override
                    public void stop() {
                        socket.stop();
                    }

                    @Override
                    public int getLocalPort() {
                        return socket.getLocalPort();
                    }
                };
            }
            catch (Exception e) {
                handler.onCompleted(e);
            }
        });
        return holder.held;
    }

    public static AsyncServerSocket listenSecure(AsyncServer server, final SSLContext sslContext, final InetAddress host, final int port, final ListenCallback handler) {
        final SSLEngineSNIConfigurator conf = new SSLEngineSNIConfigurator() {
            @Override
            public SSLEngine createEngine(SSLContext sslContext, String peerHost, int peerPort) {
                SSLEngine engine = super.createEngine(sslContext, peerHost, peerPort);
//                String[] ciphers = engine.getEnabledCipherSuites();
//                for (String cipher: ciphers) {
//                    Log.i(LOGTAG, cipher);
//                }

                // todo: what's this for? some vestigal vysor code i think. required by audio mirroring?
                engine.setEnabledCipherSuites(new String[] { "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384" });
                return engine;
            }
        };
        return server.listen(host, port, new ListenCallback() {
            @Override
            public void onAccepted(final AsyncSocket socket) {
                AsyncSSLSocketWrapper.handshake(socket, null, port, conf.createEngine(sslContext, null, port), null, null, false,
                        (e, sslSocket) -> {
                            if (e != null) {
                                // chrome seems to do some sort of SSL probe and cancels handshakes. not sure why.
                                // i suspect it is to pick an optimal strong cipher.
                                // seeing a lot of the following in the log (but no actual connection errors)
                                // javax.net.ssl.SSLHandshakeException: error:10000416:SSL routines:OPENSSL_internal:SSLV3_ALERT_CERTIFICATE_UNKNOWN
                                // seen on Shield TV running API 26
                                // todo fix: conscrypt ssl context?
//                                Log.e(LOGTAG, "Error while handshaking", e);
                                socket.close();
                                return;
                            }
                            handler.onAccepted(sslSocket);
                        });
            }

            @Override
            public void onListening(AsyncServerSocket socket) {
                handler.onListening(socket);
            }

            @Override
            public void onCompleted(Exception ex) {
                handler.onCompleted(ex);
            }
        });
    }
}
