// Copyright 2004-present Facebook. All Rights Reserved.

package com.koushikdutta.async.stetho;

import android.util.Base64;
import android.util.Base64OutputStream;

import com.facebook.stetho.inspector.network.NetworkEventReporter;
import com.facebook.stetho.inspector.network.NetworkPeerManager;
import com.facebook.stetho.inspector.network.ResponseHandler;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.util.StreamUtility;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import javax.annotation.Nullable;

/**
 * Implementation of {@link NetworkEventReporter} which allows callers to inform the Stetho
 * system of network traffic.  Callers can safely eagerly access this class and store a
 * reference if they wish.  When WebKit Inspector clients are connected, the internal
 * implementation will be automatically wired up to them.
 */
class NetworkEventReporterWrapper implements NetworkEventReporter {
    NetworkEventReporter wrapped = com.facebook.stetho.inspector.network.NetworkEventReporterImpl.get();

    private static NetworkEventReporterWrapper instance;
    public synchronized static NetworkEventReporterWrapper get() {
        if (instance == null)
            instance = new NetworkEventReporterWrapper();
        return instance;
    }

    @Override
    public boolean isEnabled() {
        return wrapped.isEnabled();
    }

    @Override
    public void requestWillBeSent(InspectorRequest inspectorRequest) {
        wrapped.requestWillBeSent(inspectorRequest);
    }

    @Override
    public void responseHeadersReceived(InspectorResponse inspectorResponse) {
        wrapped.responseHeadersReceived(inspectorResponse);
    }

    @Override
    public void httpExchangeFailed(String s, String s2) {
        wrapped.httpExchangeFailed(s, s2);
    }

    @Nullable
    @Override
    public InputStream interpretResponseStream(
    String requestId,
    @Nullable String contentType,
    @Nullable String contentEncoding,
    @Nullable InputStream availableInputStream,
    ResponseHandler responseHandler) {
        return null;
    }

    @Nullable
    private NetworkPeerManager getPeerManagerIfEnabled() {
        NetworkPeerManager peerManager = NetworkPeerManager.getInstanceOrNull();
        if (peerManager != null && peerManager.hasRegisteredPeers()) {
            return peerManager;
        }
        return null;
    }

    public DataEmitter interpretResponseEmitter(final String requestId, @Nullable DataEmitter body, final boolean b64Encode) {
        final NetworkPeerManager peerManager = getPeerManagerIfEnabled();
        if (peerManager == null)
            return null;

        final WritableByteChannel channel;
        try {
            if (b64Encode) {
                final Base64OutputStream b64out = new Base64OutputStream(peerManager.getResponseBodyFileManager().openResponseBodyFile(requestId, false), Base64.DEFAULT);
                channel = Channels.newChannel(b64out);
            }
            else {
                channel = ((FileOutputStream)peerManager.getResponseBodyFileManager().openResponseBodyFile(requestId, false)).getChannel();
            }
        }
        catch (IOException e) {
            return null;
        }

        FilteredDataEmitter ret = new FilteredDataEmitter() {
            ByteBufferList pending = new ByteBufferList();

            @Override
            protected void report(Exception e) {
                super.report(e);
                StreamUtility.closeQuietly(channel);
                if (e == null)
                    responseReadFinished(requestId);
                else
                    responseReadFailed(requestId, e.toString());
            }

            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                int amount = bb.remaining();
                ByteBuffer[] original = bb.getAllArray();
                ByteBuffer[] copy = new ByteBuffer[original.length];
                for (int i = 0; i < original.length; i++) {
                    copy[i] = original[i].duplicate();
                }
                try {
                    for (ByteBuffer c: copy) {
                        channel.write(c);
                    }
                }
                catch (IOException ignored) {
                    StreamUtility.closeQuietly(channel);
                }
                pending.addAll(original);
                dataReceived(requestId, amount, amount);
                super.onDataAvailable(emitter, pending);
            }
        };
        ret.setDataEmitter(body);
        return ret;
    }


    @Override
    public void responseReadFailed(String s, String s2) {
        wrapped.responseReadFailed(s, s2);
    }

    @Override
    public void responseReadFinished(String s) {
        wrapped.responseReadFinished(s);
    }

    @Override
    public void dataSent(String s, int i, int i2) {
        wrapped.dataSent(s, i, i2);
    }

    @Override
    public void dataReceived(String s, int i, int i2) {
        wrapped.dataReceived(s, i, i2);
    }
}
