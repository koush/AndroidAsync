package com.koushikdutta.async;

import android.os.Build;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.util.Allocator;
import com.koushikdutta.async.wrapper.AsyncSocketWrapper;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
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
    public interface HandshakeCallback {
        public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket);
    }

    static SSLContext defaultSSLContext;

    AsyncSocket mSocket;
    BufferedDataEmitter mEmitter;
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
            if (Build.VERSION.SDK_INT <= 15)
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
                callback.onHandshakeCompleted(new SSLException(ex), null);
            }
        });
        try {
            wrapper.engine.beginHandshake();
            wrapper.handleHandshakeStatus(wrapper.engine.getHandshakeStatus());
        } catch (SSLException e) {
            wrapper.report(e);
        }
    }

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

        // SSL needs buffering of data written during handshake.
        // aka exhcange.setDatacallback
        mEmitter = new BufferedDataEmitter(socket);

        final Allocator allocator = new Allocator();
        allocator.setMinAlloc(8192);
        final ByteBufferList transformed = new ByteBufferList();
        mEmitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                if (mUnwrapping)
                    return;
                try {
                    mUnwrapping = true;

                    if (bb.hasRemaining()) {
                        ByteBuffer all = bb.getAll();
                        bb.add(all);
                    }

                    ByteBuffer b = ByteBufferList.EMPTY_BYTEBUFFER;
                    while (true) {
                        if (b.remaining() == 0 && bb.size() > 0) {
                            b = bb.remove();
                        }
                        int remaining = b.remaining();
                        int before = transformed.remaining();

                        SSLEngineResult res;
                        {
                            // wrap to prevent access to the readBuf
                            ByteBuffer readBuf = allocator.allocate();
                            res = engine.unwrap(b, readBuf);
                            addToPending(transformed, readBuf);
                            allocator.track(transformed.remaining() - before);
                        }
                        if (res.getStatus() == Status.BUFFER_OVERFLOW) {
                            allocator.setMinAlloc(allocator.getMinAlloc() * 2);
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
                        handleHandshakeStatus(res.getHandshakeStatus());
                        if (b.remaining() == remaining && before == transformed.remaining()) {
                            bb.addFirst(b);
                            break;
                        }
                    }

                    Util.emitAllData(AsyncSSLSocketWrapper.this, transformed);
                }
                catch (SSLException ex) {
                    ex.printStackTrace();
                    report(ex);
                }
                finally {
                    mUnwrapping = false;
                }
            }
        });
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
            write(ByteBufferList.EMPTY_BYTEBUFFER);
        }

        if (status == HandshakeStatus.NEED_UNWRAP) {
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
                    Exception peerUnverifiedCause = null;
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
                        catch (GeneralSecurityException ex) {
                            peerUnverifiedCause = ex;
                        }
                        catch (SSLException ex) {
                            peerUnverifiedCause = ex;
                        }
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
                if (mWriteableCallback != null)
                    mWriteableCallback.onWriteable();
                mEmitter.onDataAvailable();
            }
        }
        catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
        catch (GeneralSecurityException ex) {
            report(ex);
        }
        catch (AsyncSSLException ex) {
            report(ex);
        }
    }

    private void writeTmp(ByteBuffer mWriteTmp) {
        mWriteTmp.flip();
        if (mWriteTmp.remaining() > 0)
            mSink.write(mWriteTmp);
        assert !mWriteTmp.hasRemaining();
    }


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
                handleHandshakeStatus(res.getHandshakeStatus());
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
            if (finishedHandshake && bb.remaining() == 0)
                break;
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
                    handleHandshakeStatus(res.getHandshakeStatus());
                }
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
            mSocket.setDataCallback(new NullDataCallback());
            mSocket.end();
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

    @Override
    public X509Certificate[] getPeerCertificates() {
        return peerCertificates;
    }

    @Override
    public String charset() {
        return null;
    }
}
