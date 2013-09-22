package com.koushikdutta.async;

import android.os.Build;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.wrapper.AsyncSocketWrapper;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class AsyncSSLSocketWrapper implements AsyncSocketWrapper, AsyncSSLSocket {
    AsyncSocket mSocket;
    BufferedDataEmitter mEmitter;
    BufferedDataSink mSink;
    ByteBuffer mReadTmp = ByteBufferList.obtain(8192);
    boolean mUnwrapping = false;
    HostnameVerifier hostnameVerifier;

    @Override
    public void end() {
        mSocket.end();
    }

    public AsyncSSLSocketWrapper(AsyncSocket socket, String host, int port) {
        this(socket, host, port, sslContext, null, null, true);
    }

    TrustManager[] trustManagers;
    boolean clientMode;

    public AsyncSSLSocketWrapper(AsyncSocket socket, String host, int port, SSLContext sslContext, TrustManager[] trustManagers, HostnameVerifier verifier, boolean clientMode) {
        mSocket = socket;
        hostnameVerifier = verifier;
        this.clientMode = clientMode;
        this.trustManagers = trustManagers;

        if (sslContext == null)
            sslContext = AsyncSSLSocketWrapper.sslContext;

        if (host != null) {
            engine = sslContext.createSSLEngine(host, port);
        }
        else {
            engine = sslContext.createSSLEngine();
        }
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

        // SSL needs buffering of data written during handshake.
        // aka exhcange.setDatacallback
        mEmitter = new BufferedDataEmitter(socket);

        final ByteBufferList transformed = new ByteBufferList();
        mEmitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                if (mUnwrapping)
                    return;
                try {
                    mUnwrapping = true;

                    mReadTmp.position(0);
                    mReadTmp.limit(mReadTmp.capacity());

                    ByteBuffer b = ByteBufferList.EMPTY_BYTEBUFFER;
                    while (true) {
                        if (b.remaining() == 0 && bb.size() > 0) {
                            b = bb.remove();
                        }
                        int remaining = b.remaining();

                        SSLEngineResult res = engine.unwrap(b, mReadTmp);
                        if (res.getStatus() == Status.BUFFER_OVERFLOW) {
                            addToPending(transformed);
                            mReadTmp = ByteBufferList.obtain(mReadTmp.remaining() * 2);
                            remaining = -1;
                        }
                        else if (res.getStatus() == Status.BUFFER_UNDERFLOW) {
                            bb.addFirst(b);
                            if (bb.size() <= 1) {
                                break;
                            }
                            // pack it
                            remaining = -1;
                            b = bb.getAll();
                            bb.addFirst(b);
                            b = ByteBufferList.EMPTY_BYTEBUFFER;
                        }
                        handleResult(res);
                        if (b.remaining() == remaining) {
                            bb.addFirst(b);
                            break;
                        }
                    }

                    addToPending(transformed);
                    Util.emitAllData(AsyncSSLSocketWrapper.this, transformed);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    report(ex);
                }
                finally {
                    mUnwrapping = false;
                }
            }
        });
    }

    void addToPending(ByteBufferList out) {
        if (mReadTmp.position() > 0) {
            mReadTmp.flip();
            out.add(mReadTmp);
            mReadTmp = ByteBufferList.obtain(mReadTmp.capacity());
        }
    }

    static SSLContext sslContext;

    static {
        // following is the "trust the system" certs setup
        try {
            // critical extension 2.5.29.15 is implemented improperly prior to 4.0.3.
            // https://code.google.com/p/android/issues/detail?id=9307
            // https://groups.google.com/forum/?fromgroups=#!topic/netty/UCfqPPk5O4s
            // certs that use this extension will throw in Cipher.java.
            // fallback is to use a custom SSLContext, and hack around the x509 extension.
            if (Build.VERSION.SDK_INT <= 15)
                throw new Exception();
            sslContext = SSLContext.getInstance("Default");
        }
        catch (Exception ex) {
            try {
                sslContext = SSLContext.getInstance("TLS");
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
                sslContext.init(null, trustAllCerts, null);
            }
            catch (Exception ex2) {
                ex.printStackTrace();
                ex2.printStackTrace();
            }
        }
    }

    SSLEngine engine;
    boolean finishedHandshake = false;

    private String mHost;

    public String getHost() {
        return mHost;
    }

    private int mPort;

    public int getPort() {
        return mPort;
    }

    private void handleResult(SSLEngineResult res) {
        if (res.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            final Runnable task = engine.getDelegatedTask();
            task.run();
        }

        if (res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
            write(ByteBufferList.EMPTY_BYTEBUFFER);
        }

        if (res.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
            mEmitter.onDataAvailable();
        }

        try {
            if (!finishedHandshake && (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING || engine.getHandshakeStatus() == HandshakeStatus.FINISHED)) {
                if (clientMode) {
                    TrustManager[] trustManagers = this.trustManagers;
                    if (trustManagers == null) {
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        tmf.init((KeyStore) null);
                        trustManagers = tmf.getTrustManagers();
                    }
                    boolean trusted = false;
                    for (TrustManager tm : trustManagers) {
                        try {
                            X509TrustManager xtm = (X509TrustManager) tm;
                            peerCertificates = (X509Certificate[]) engine.getSession().getPeerCertificates();
                            xtm.checkServerTrusted(peerCertificates, "SSL");
                            if (mHost != null) {
                                if (hostnameVerifier == null) {
                                    StrictHostnameVerifier verifier = new StrictHostnameVerifier();
                                    verifier.verify(mHost, StrictHostnameVerifier.getCNs(peerCertificates[0]), StrictHostnameVerifier.getDNSSubjectAlts(peerCertificates[0]));
                                }
                                else {
                                    hostnameVerifier.verify(mHost, engine.getSession());
                                }
                            }
                            trusted = true;
                            break;
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                    finishedHandshake = true;
                    if (!trusted) {
                        AsyncSSLException e = new AsyncSSLException();
                        report(e);
                        if (!e.getIgnore())
                            throw e;
                    }
                }
                if (mWriteableCallback != null)
                    mWriteableCallback.onWriteable();
                mEmitter.onDataAvailable();
            }
        }
        catch (Exception ex) {
            report(ex);
        }
    }

    private void writeTmp(ByteBuffer mWriteTmp) {
        mWriteTmp.flip();
        if (mWriteTmp.remaining() > 0)
            mSink.write(mWriteTmp);
        assert !mWriteTmp.hasRemaining();
    }

    private boolean mWrapping = false;

    int calculateAlloc(int remaining) {
        // alloc 50% more than we need for writing
        int alloc = remaining * 3 / 2;
        if (alloc == 0)
            alloc = 8182;
        return alloc;
    }

    @Override
    public void write(ByteBuffer bb) {
        if (mWrapping)
            return;
        if (mSink.remaining() > 0)
            return;
        mWrapping = true;
        int remaining;
        SSLEngineResult res = null;
        ByteBuffer mWriteTmp = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
        do {
            // if the handshake is finished, don't send
            // 0 bytes of data, since that makes the ssl connection die.
            // it wraps a 0 byte package, and craps out.
            if (finishedHandshake && bb.remaining() == 0) {
                mWrapping = false;
                return;
            }
            remaining = bb.remaining();
            try {
                res = engine.wrap(bb, mWriteTmp);
                writeTmp(mWriteTmp);
                int previousCapacity = mWriteTmp.capacity();
                ByteBufferList.reclaim(mWriteTmp);
                mWriteTmp = null;
                if (res.getStatus() == Status.BUFFER_OVERFLOW) {
                    mWriteTmp = ByteBufferList.obtain(previousCapacity * 2);
                    remaining = -1;
                }
                else {
                    mWriteTmp = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
                }
                handleResult(res);
            }
            catch (SSLException e) {
                report(e);
            }
        }
        while ((remaining != bb.remaining() || (res != null && res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP)) && mSink.remaining() == 0);
        ByteBufferList.reclaim(mWriteTmp);
        mWrapping = false;
    }

    @Override
    public void write(ByteBufferList bb) {
        if (mWrapping)
            return;
        if (mSink.remaining() > 0)
            return;
        mWrapping = true;
        int remaining;
        SSLEngineResult res = null;
        ByteBuffer mWriteTmp = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
        do {
            // if the handshake is finished, don't send
            // 0 bytes of data, since that makes the ssl connection die.
            // it wraps a 0 byte package, and craps out.
            if (finishedHandshake && bb.remaining() == 0) {
                mWrapping = false;
                return;
            }
            remaining = bb.remaining();
            try {
                ByteBuffer[] arr = bb.getAllArray();
                res = engine.wrap(arr, mWriteTmp);
                bb.addAll(arr);
                writeTmp(mWriteTmp);
                int previousCapacity = mWriteTmp.capacity();
                ByteBufferList.reclaim(mWriteTmp);
                mWriteTmp = null;
                if (res.getStatus() == Status.BUFFER_OVERFLOW) {
                    mWriteTmp = ByteBufferList.obtain(previousCapacity * 2);
                    remaining = -1;
                }
                else {
                    mWriteTmp = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
                }
                handleResult(res);
            }
            catch (SSLException e) {
                report(e);
            }
        }
        while ((remaining != bb.remaining() || (res != null && res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP)) && mSink.remaining() == 0);
        ByteBufferList.reclaim(mWriteTmp);
        mWrapping = false;
    }

    WritableCallback mWriteableCallback;

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mWriteableCallback = handler;
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return mWriteableCallback;
    }

    private void report(Exception e) {
        CompletedCallback cb = getEndCallback();
        if (cb != null)
            cb.onCompleted(e);
    }

    DataCallback mDataCallback;

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

    @Override
    public void setEndCallback(CompletedCallback callback) {
        mSocket.setEndCallback(callback);
    }

    @Override
    public CompletedCallback getEndCallback() {
        return mSocket.getEndCallback();
    }

    @Override
    public void pause() {
        mSocket.pause();
    }

    @Override
    public void resume() {
        mSocket.resume();
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

    X509Certificate[] peerCertificates;

    @Override
    public X509Certificate[] getPeerCertificates() {
        return peerCertificates;
    }
}
