package com.koushikdutta.async;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import junit.framework.Assert;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;

public class SSLDataExchange implements DataTransformer, DataExchange {
    DataExchange mExchange;
    BufferedDataEmitter mEmitter = new BufferedDataEmitter();
    BufferedDataSink mSink;
    ByteBuffer mReadTmp = ByteBuffer.allocate(8192);
    boolean mUnwrapping = false;
    public SSLDataExchange(DataExchange exchange) {
        mExchange = exchange;

        engine.setUseClientMode(true);
        mSink = new BufferedDataSink(exchange);
        
        // SSL needs buffering of data written during handshake.
        // aka exhcange.setDatacallback
        exchange.setDataCallback(mEmitter);
        
        mEmitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                try {
                    if (mUnwrapping)
                        return;
                    mUnwrapping = true;
                    
                    ByteBufferList out = new ByteBufferList();

                    mReadTmp.position(0);
                    mReadTmp.limit(mReadTmp.capacity());
                    ByteBuffer b;
                    if (bb.size() > 1)
                        b = bb.read(bb.remaining());
                    else if (bb.size() == 1)
                        b = bb.peek();
                    else {
                        b = ByteBuffer.allocate(0);
                    }
                    
                    while (true) {
                        int remaining = b.remaining();
                       
                        SSLEngineResult res = engine.unwrap(b, mReadTmp);
                        if (res.getStatus() == Status.BUFFER_OVERFLOW) {
                            addToPending(out);
                            mReadTmp = ByteBuffer.allocate(mReadTmp.remaining() * 2);
                            remaining = -1;
                        }
                        handleResult(res);
                        if (b.remaining() == remaining)
                            break;
                    }
                    
                    addToPending(out);
                    Util.emitAllData(SSLDataExchange.this, out);
                }
                catch (Exception ex) {
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
            mReadTmp.limit(mReadTmp.position());
            mReadTmp.position(0);
            out.add(mReadTmp);
            mReadTmp = ByteBuffer.allocate(mReadTmp.capacity());
        }
    }

    static {
        try {
            ctx = SSLContext.getInstance("Default");
        }
        catch (Exception ex) {
        }
    }
    static SSLContext ctx;

    SSLEngine engine = ctx.createSSLEngine();
    boolean finishedHandshake = false;

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
        return false;
    }
    
    private String mHost;
    public String getHost() {
        return mHost;
    }
    public void setHost(String host) {
        mHost = host;
    }
    
    private int mPort;
    public int getPort() {
        return mPort;
    }
    public void setPort(int port) {
        mPort = port;
    }
    
    private void handleResult(SSLEngineResult res) {
        if (res.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            final Runnable task = engine.getDelegatedTask();
            task.run();
        }
        
        if (res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
            write(ByteBuffer.allocate(0));
        }

        if (res.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
            mEmitter.onDataAvailable();
        }
        
        try {
            if (!finishedHandshake && (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING || engine.getHandshakeStatus() == HandshakeStatus.FINISHED)) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                boolean trusted = false;
                for (TrustManager tm : tmf.getTrustManagers()) {
                    try {
                        X509TrustManager xtm = (X509TrustManager) tm;
                        X509Certificate[] certs = (X509Certificate[]) engine.getSession().getPeerCertificates();
                        xtm.checkServerTrusted(certs, "SSL");
                        if (mHost != null) {
                            StrictHostnameVerifier verifier = new StrictHostnameVerifier();
                            verifier.verify(mHost, StrictHostnameVerifier.getCNs(certs[0]), StrictHostnameVerifier.getDNSSubjectAlts(certs[0]));
                        }
                        trusted = true;
                        break;
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                finishedHandshake = true;
                if (!trusted)
                    throw new SSLPeerUnverifiedException("Not trusted by any of the system trust managers.");
                Assert.assertNotNull(mWriteableCallback);
                mWriteableCallback.onWriteable();
                mEmitter.onDataAvailable();
            }
        }
        catch (Exception ex) {
            report(ex);
        }
    }
    
    private void report(Exception e) {
        if (mErrorCallback != null)
            mErrorCallback.onException(e);
    }
    
    private void writeTmp() {
        mWriteTmp.limit(mWriteTmp.position());
        mWriteTmp.position(0);
        if (mWriteTmp.remaining() > 0)
            mSink.write(mWriteTmp);
    }
    
    boolean checkWrapResult(SSLEngineResult res) {
        if (res.getStatus() == Status.BUFFER_OVERFLOW) {
            mWriteTmp = ByteBuffer.allocate(mWriteTmp.remaining() * 2);
            return false;
        }
        return true;
    }

    private boolean mWrapping = false;
    ByteBuffer mWriteTmp = ByteBuffer.allocate(8192);
    @Override
    public void write(ByteBuffer bb) {
        mWrapping = true;
        int remaining;
        SSLEngineResult res = null;
        do {
            remaining = bb.remaining();
            mWriteTmp.position(0);
            mWriteTmp.limit(mWriteTmp.capacity());
            try {
                res = engine.wrap(bb, mWriteTmp);
                if (!checkWrapResult(res))
                    remaining = -1;
                writeTmp();
                handleResult(res);
            }
            catch (SSLException e) {
                report(e);
            }
        }
        while (remaining != bb.remaining() || (res != null && res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP));
        mWrapping = false;
    }

    @Override
    public void write(ByteBufferList bb) {
        if (mWrapping)
            return;
        mWrapping = true;
        int remaining;
        SSLEngineResult res = null;
        do {
            remaining = bb.remaining();
            mWriteTmp.position(0);
            mWriteTmp.limit(mWriteTmp.capacity());
            try {
                res = engine.wrap(bb.toArray(), mWriteTmp);
                if (!checkWrapResult(res))
                    remaining = -1;
                writeTmp();
                handleResult(res);
            }
            catch (SSLException e) {
                report(e);
            }
        }
        while (remaining != bb.remaining() || (res != null && res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP));
        mWrapping = false;
    }

    WritableCallback mWriteableCallback;
    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mWriteableCallback = handler;
    }
    
    private ExceptionCallback mErrorCallback;
    @Override
    public void setExceptionCallback(ExceptionCallback callback) {
        mErrorCallback = callback;        
    }
    @Override
    public ExceptionCallback getExceptionCallback() {
        return mErrorCallback;
    }
    @Override
    public WritableCallback getWriteableCallback() {
        return mWriteableCallback;
    }

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        mEmitter.onDataAvailable(emitter, bb);
    }
}
