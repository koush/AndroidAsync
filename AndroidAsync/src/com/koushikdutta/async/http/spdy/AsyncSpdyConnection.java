package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataEmitter;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.ErrorCode;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.FrameReader;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.FrameWriter;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.Header;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.HeadersMode;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.Http20Draft13;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.Ping;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.Settings;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.Spdy3;
import com.koushikdutta.async.http.spdy.okhttp.internal.spdy.Variant;
import com.koushikdutta.async.http.spdy.okio.BufferedSource;
import com.koushikdutta.async.http.spdy.okio.ByteString;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.koushikdutta.async.http.spdy.okhttp.internal.spdy.Settings.DEFAULT_INITIAL_WINDOW_SIZE;

/**
 * Created by koush on 7/16/14.
 */
public class AsyncSpdyConnection implements FrameReader.Handler {
    BufferedDataEmitter emitter;
    AsyncSocket socket;
    FrameReader reader;
    FrameWriter writer;
    Variant variant;
    SpdySocket zero = new SpdySocket(0, false, false, null);
    ByteBufferListSource source = new ByteBufferListSource();
    Hashtable<Integer, SpdySocket> sockets = new Hashtable<Integer, SpdySocket>();
    Protocol protocol;
    boolean client = true;

    private class SpdySocket implements AsyncSocket {
        long bytesLeftInWriteWindow;
        WritableCallback writable;
        final int id;
        CompletedCallback closedCallback;
        CompletedCallback endCallback;
        DataCallback dataCallback;
        ByteBufferList pending = new ByteBufferList();

        public SpdySocket(int id, boolean outFinished, boolean inFinished, List<Header> headerBlock) {
            this.id = id;
        }

        private void report(Exception e) {
            if (endCallback != null)
                endCallback.onCompleted(e);
        }

        public boolean isLocallyInitiated() {
            boolean streamIsClient = ((id & 1) == 1);
            return client == streamIsClient;
        }

        public void addBytesToWriteWindow(long delta) {
            long prev = bytesLeftInWriteWindow;
            bytesLeftInWriteWindow += delta;
            if (writable != null && bytesLeftInWriteWindow > 0 && prev <= 0)
                writable.onWriteable();
        }

        @Override
        public AsyncServer getServer() {
            return socket.getServer();
        }

        @Override
        public void setDataCallback(DataCallback callback) {
            dataCallback = callback;
        }

        @Override
        public DataCallback getDataCallback() {
            return dataCallback;
        }

        @Override
        public boolean isChunked() {
            return false;
        }

        boolean paused;
        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public void resume() {
            paused = false;
        }

        @Override
        public void close() {

        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public void setEndCallback(CompletedCallback callback) {
            endCallback = callback;
        }

        @Override
        public CompletedCallback getEndCallback() {
            return endCallback;
        }

        @Override
        public String charset() {
            return null;
        }

        @Override
        public void write(ByteBufferList bb) {

        }

        @Override
        public void setWriteableCallback(WritableCallback handler) {
            writable = handler;
        }

        @Override
        public WritableCallback getWriteableCallback() {
            return writable;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void end() {
        }

        @Override
        public void setClosedCallback(CompletedCallback handler) {
            closedCallback = handler;
        }

        @Override
        public CompletedCallback getClosedCallback() {
            return closedCallback;
        }
    }

    public AsyncSpdyConnection(AsyncSocket socket, Protocol protocol) {
        this.protocol = protocol;
        this.socket = socket;
        emitter = new BufferedDataEmitter(socket);
        emitter.setDataCallback(callback);

        if (protocol == Protocol.SPDY_3) {
            variant = new Spdy3();
        }
        else if (protocol == Protocol.HTTP_2) {
            variant = new Http20Draft13();
        }
        reader = variant.newReader(source, true);
    }

    DataCallback callback = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            bb.get(source);
            if (!reader.canProcessFrame(source))
                return;
            try {
                reader.nextFrame(AsyncSpdyConnection.this);
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    };

    /** Even, positive numbered streams are pushed streams in HTTP/2. */
    private boolean pushedStream(int streamId) {
        return protocol == Protocol.HTTP_2 && streamId != 0 && (streamId & 1) == 0;
    }

    @Override
    public void data(boolean inFinished, int streamId, BufferedSource source, int length) throws IOException {
        if (pushedStream(streamId)) {
            throw new AssertionError("push");
//            pushDataLater(streamId, source, length, inFinished);
//            return;
        }
        SpdySocket socket = sockets.get(streamId);
        if (socket == null) {
            writer.rstStream(streamId, ErrorCode.INVALID_STREAM);
            source.skip(length);
            return;
        }
        if (source != this.source)
            throw new AssertionError();
        this.source.get(socket.pending, length);
        Util.emitAllData(socket, socket.pending);
        if (inFinished) {
            socket.report(null);
        }
    }

    private int lastGoodStreamId;
    private int nextStreamId;
    @Override
    public void headers(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, List<Header> headerBlock, HeadersMode headersMode) {
        /*
        if (pushedStream(streamId)) {
            throw new AssertionError("push");
//            pushHeadersLater(streamId, headerBlock, inFinished);
//            return;
        }

        // If we're shutdown, don't bother with this stream.
        if (shutdown) return;

        SpdySocket socket = sockets.get(streamId);

        if (socket == null) {
            // The headers claim to be for an existing stream, but we don't have one.
            if (headersMode.failIfStreamAbsent()) {
                try {
                    writer.rstStream(streamId, ErrorCode.INVALID_STREAM);
                    return;
                }
                catch (IOException e) {
                    throw new AssertionError(e);
                }
            }

            // If the stream ID is less than the last created ID, assume it's already closed.
            if (streamId <= lastGoodStreamId) return;

            // If the stream ID is in the client's namespace, assume it's already closed.
            if (streamId % 2 == nextStreamId % 2) return;

            // Create a stream.
            socket = new SpdySocket(streamId, outFinished, inFinished, headerBlock);
            lastGoodStreamId = streamId;
            sockets.put(streamId, socket);
            handler.receive(newStream);
            return;
        }

        // The headers claim to be for a new stream, but we already have one.
        if (headersMode.failIfStreamPresent()) {
            stream.closeLater(ErrorCode.PROTOCOL_ERROR);
            removeStream(streamId);
            return;
        }

        // Update an existing stream.
        stream.receiveHeaders(headerBlock, headersMode);
        if (inFinished) stream.receiveFin();
        */
    }

    @Override
    public void rstStream(int streamId, ErrorCode errorCode) {
        if (pushedStream(streamId)) {
            throw new AssertionError("push");
//            pushResetLater(streamId, errorCode);
//            return;
        }
        SpdySocket rstStream = sockets.remove(streamId);
        if (rstStream != null) {
            rstStream.report(new IOException(errorCode.toString()));
        }
    }

    Settings peerSettings = new Settings();
    private boolean receivedInitialPeerSettings = false;
    @Override
    public void settings(boolean clearPrevious, Settings settings) {
        long delta = 0;
        int priorWriteWindowSize = peerSettings.getInitialWindowSize(DEFAULT_INITIAL_WINDOW_SIZE);
        if (clearPrevious)
            peerSettings.clear();
        peerSettings.merge(settings);
        try {
            writer.ackSettings();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        int peerInitialWindowSize = peerSettings.getInitialWindowSize(DEFAULT_INITIAL_WINDOW_SIZE);
        if (peerInitialWindowSize != -1 && peerInitialWindowSize != priorWriteWindowSize) {
            delta = peerInitialWindowSize - priorWriteWindowSize;
            if (!receivedInitialPeerSettings) {
                zero.addBytesToWriteWindow(delta);
                receivedInitialPeerSettings = true;
            }
        }
        for (SpdySocket socket: sockets.values()) {
            socket.addBytesToWriteWindow(delta);
        }
    }

    @Override
    public void ackSettings() {
    }

    private Map<Integer, Ping> pings;
    private void writePing(boolean reply, int payload1, int payload2, Ping ping) throws IOException {
        if (ping != null) ping.send();
        writer.ping(reply, payload1, payload2);
    }

    private synchronized Ping removePing(int id) {
        return pings != null ? pings.remove(id) : null;
    }

    @Override
    public void ping(boolean ack, int payload1, int payload2) {
        if (ack) {
            Ping ping = removePing(payload1);
            if (ping != null) {
                ping.receive();
            }
        } else {
            // Send a reply to a client ping if this is a server and vice versa.
            try {
                writePing(true, payload1, payload2, null);
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }

    boolean shutdown;
    @Override
    public void goAway(int lastGoodStreamId, ErrorCode errorCode, ByteString debugData) {
        shutdown = true;

        // Fail all streams created after the last good stream ID.
        for (Iterator<Map.Entry<Integer, SpdySocket>> i = sockets.entrySet().iterator();
             i.hasNext(); ) {
            Map.Entry<Integer, SpdySocket> entry = i.next();
            int streamId = entry.getKey();
            if (streamId > lastGoodStreamId && entry.getValue().isLocallyInitiated()) {
                entry.getValue().report(new IOException(ErrorCode.REFUSED_STREAM.toString()));
                i.remove();
            }
        }
    }

    @Override
    public void windowUpdate(int streamId, long windowSizeIncrement) {
        System.out.println("fff");

    }

    @Override
    public void priority(int streamId, int streamDependency, int weight, boolean exclusive) {
        System.out.println("fff");

    }

    @Override
    public void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders) throws IOException {
        System.out.println("fff");

    }

    @Override
    public void alternateService(int streamId, String origin, ByteString protocol, String host, int port, long maxAge) {
        System.out.println("fff");

    }
}
