package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.Protocol;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.koushikdutta.async.http.spdy.Settings.DEFAULT_INITIAL_WINDOW_SIZE;

/**
 * Created by koush on 7/16/14.
 */
public class AsyncSpdyConnection implements FrameReader.Handler {
    AsyncSocket socket;
    BufferedDataSink bufferedSocket;
    FrameReader reader;
    FrameWriter writer;
    Variant variant;
    Hashtable<Integer, SpdySocket> sockets = new Hashtable<Integer, SpdySocket>();
    Protocol protocol;
    boolean client = true;

    /**
     * Returns a new locally-initiated stream.
     *
     * @param out true to create an output stream that we can use to send data to the remote peer.
     *     Corresponds to {@code FLAG_FIN}.
     * @param in true to create an input stream that the remote peer can use to send data to us.
     *     Corresponds to {@code FLAG_UNIDIRECTIONAL}.
     */
    public SpdySocket newStream(List<Header> requestHeaders, boolean out, boolean in) {
        return newStream(0, requestHeaders, out, in);
    }

    private SpdySocket newStream(int associatedStreamId, List<Header> requestHeaders, boolean out,
                                 boolean in) {
        boolean outFinished = !out;
        boolean inFinished = !in;
        SpdySocket socket;
        int streamId;

        if (shutdown) {
            return null;
        }

        streamId = nextStreamId;
        nextStreamId += 2;
        socket = new SpdySocket(streamId, outFinished, inFinished, requestHeaders);
        if (socket.isOpen()) {
            sockets.put(streamId, socket);
//            setIdle(false);
        }
        try {
            if (associatedStreamId == 0) {
                writer.synStream(outFinished, inFinished, streamId, associatedStreamId,
                requestHeaders);
            } else if (client) {
                throw new IllegalArgumentException("client streams shouldn't have associated stream IDs");
            } else { // HTTP/2 has a PUSH_PROMISE frame.
                writer.pushPromise(associatedStreamId, streamId, requestHeaders);
            }

            return socket;
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    int totalWindowRead;
    void updateWindowRead(int length) {
        totalWindowRead += length;
        if (totalWindowRead >= okHttpSettings.getInitialWindowSize(DEFAULT_INITIAL_WINDOW_SIZE) / 2) {
            try {
                writer.windowUpdate(0, totalWindowRead);
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
            totalWindowRead = 0;
        }
    }

    public class SpdySocket implements AsyncSocket {
        long bytesLeftInWriteWindow = AsyncSpdyConnection.this.peerSettings.getInitialWindowSize(Settings.DEFAULT_INITIAL_WINDOW_SIZE);
        WritableCallback writable;
        final int id;
        CompletedCallback closedCallback;
        CompletedCallback endCallback;
        DataCallback dataCallback;
        ByteBufferList pending = new ByteBufferList();
        SimpleFuture<List<Header>> headers = new SimpleFuture<List<Header>>();
        boolean isOpen = true;
        int totalWindowRead;

        public AsyncSpdyConnection getConnection() {
            return AsyncSpdyConnection.this;
        }

        public SimpleFuture<List<Header>> headers() {
            return headers;
        }

        void updateWindowRead(int length) {
            totalWindowRead += length;
            if (totalWindowRead >= okHttpSettings.getInitialWindowSize(DEFAULT_INITIAL_WINDOW_SIZE) / 2) {
                try {
                    writer.windowUpdate(id, totalWindowRead);
                }
                catch (IOException e) {
                    throw new AssertionError(e);
                }
                totalWindowRead = 0;
            }
            AsyncSpdyConnection.this.updateWindowRead(length);
        }

        public SpdySocket(int id, boolean outFinished, boolean inFinished, List<Header> headerBlock) {
            this.id = id;
        }

        public boolean isLocallyInitiated() {
            boolean streamIsClient = ((id & 1) == 1);
            return client == streamIsClient;
        }

        public void addBytesToWriteWindow(long delta) {
            long prev = bytesLeftInWriteWindow;
            bytesLeftInWriteWindow += delta;
            if (bytesLeftInWriteWindow > 0 && prev <= 0)
                Util.writable(writable);
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
            isOpen = false;
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

        ByteBufferList writing = new ByteBufferList();
        @Override
        public void write(ByteBufferList bb) {
            int canWrite = (int)Math.min(bytesLeftInWriteWindow, AsyncSpdyConnection.this.bytesLeftInWriteWindow);
            canWrite = Math.min(bb.remaining(), canWrite);
            if (canWrite == 0) {
                return;
            }
            if (canWrite < bb.remaining()) {
                if (writing.hasRemaining())
                    throw new AssertionError("wtf");
                bb.get(writing, canWrite);
                bb = writing;
            }

            try {
                writer.data(false, id, bb);
                bytesLeftInWriteWindow -= canWrite;
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
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
            return isOpen;
        }

        @Override
        public void end() {
            try {
                writer.data(true, id, writing);
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void setClosedCallback(CompletedCallback handler) {
            closedCallback = handler;
        }

        @Override
        public CompletedCallback getClosedCallback() {
            return closedCallback;
        }

        public void receiveHeaders(List<Header> headers, HeadersMode headerMode) {
            this.headers.setComplete(headers);
        }
    }

    final Settings okHttpSettings = new Settings();
    private int nextPingId;
    private static final int OKHTTP_CLIENT_WINDOW_SIZE = 16 * 1024 * 1024;

    public AsyncSpdyConnection(AsyncSocket socket, Protocol protocol) {
        this.protocol = protocol;
        this.socket = socket;
        this.bufferedSocket = new BufferedDataSink(socket);

        if (protocol == Protocol.SPDY_3) {
            variant = new Spdy3();
        }
        else if (protocol == Protocol.HTTP_2) {
            variant = new Http20Draft13();
        }
        reader = variant.newReader(socket, this, true);
        writer = variant.newWriter(bufferedSocket, true);

        boolean client = true;
        nextStreamId = client ? 1 : 2;
        if (client && protocol == Protocol.HTTP_2) {
            nextStreamId += 2; // In HTTP/2, 1 on client is reserved for Upgrade.
        }
        nextPingId = client ? 1 : 2;
        // Flow control was designed more for servers, or proxies than edge clients.
        // If we are a client, set the flow control window to 16MiB.  This avoids
        // thrashing window updates every 64KiB, yet small enough to avoid blowing
        // up the heap.
        if (client) {
            okHttpSettings.set(Settings.INITIAL_WINDOW_SIZE, 0, OKHTTP_CLIENT_WINDOW_SIZE);
        }
    }

    /**
     * Sends a connection header if the current variant requires it. This should
     * be called after {@link Builder#build} for all new connections.
     */
    public void sendConnectionPreface() throws IOException {
        writer.connectionPreface();
        writer.settings(okHttpSettings);
        int windowSize = okHttpSettings.getInitialWindowSize(Settings.DEFAULT_INITIAL_WINDOW_SIZE);
        if (windowSize != Settings.DEFAULT_INITIAL_WINDOW_SIZE) {
            writer.windowUpdate(0, windowSize - Settings.DEFAULT_INITIAL_WINDOW_SIZE);
        }
    }

    /** Even, positive numbered streams are pushed streams in HTTP/2. */
    private boolean pushedStream(int streamId) {
        return protocol == Protocol.HTTP_2 && streamId != 0 && (streamId & 1) == 0;
    }

    @Override
    public void data(boolean inFinished, int streamId, ByteBufferList source) {
        if (pushedStream(streamId)) {
            throw new AssertionError("push");
//            pushDataLater(streamId, source, length, inFinished);
//            return;
        }
        SpdySocket socket = sockets.get(streamId);
        if (socket == null) {
            try {
                writer.rstStream(streamId, ErrorCode.INVALID_STREAM);
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
            source.recycle();
            return;
        }
        int length = source.remaining();
        source.get(socket.pending);
        socket.updateWindowRead(length);
        Util.emitAllData(socket, socket.pending);
        if (inFinished) {
            sockets.remove(streamId);
            socket.close();
            Util.end(socket, null);
        }
    }

    private int lastGoodStreamId;
    private int nextStreamId;
    @Override
    public void headers(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, List<Header> headerBlock, HeadersMode headersMode) {
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

            throw new AssertionError("unexpected receive stream");

            // Create a stream.
//            socket = new SpdySocket(streamId, outFinished, inFinished, headerBlock);
//            lastGoodStreamId = streamId;
//            sockets.put(streamId, socket);
//            handler.receive(newStream);
//            return;
        }

        // The headers claim to be for a new stream, but we already have one.
        if (headersMode.failIfStreamPresent()) {
            try {
                writer.rstStream(streamId, ErrorCode.INVALID_STREAM);
            }
            catch (IOException e) {
                throw new AssertionError(e);
            }
            sockets.remove(streamId);
            return;
        }

        // Update an existing stream.
        socket.receiveHeaders(headerBlock, headersMode);
        if (inFinished) {
            sockets.remove(streamId);
            Util.end(socket, null);
        }
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
            Util.end(rstStream, new IOException(errorCode.toString()));
        }
    }

    long bytesLeftInWriteWindow;
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
                addBytesToWriteWindow(delta);
                receivedInitialPeerSettings = true;
            }
        }
        for (SpdySocket socket: sockets.values()) {
            socket.addBytesToWriteWindow(delta);
        }
    }

    void addBytesToWriteWindow(long delta) {
        bytesLeftInWriteWindow += delta;
        for (SpdySocket socket: sockets.values()) {
            Util.writable(socket);
        }
    }

    @Override
    public void ackSettings() {
        try {
            writer.ackSettings();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
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
                Util.end(entry.getValue(), new IOException(ErrorCode.REFUSED_STREAM.toString()));
                i.remove();
            }
        }
    }

    @Override
    public void windowUpdate(int streamId, long windowSizeIncrement) {
        if (streamId == 0) {
            addBytesToWriteWindow(windowSizeIncrement);
            return;
        }
        SpdySocket socket = sockets.get(streamId);
        if (socket != null)
            socket.addBytesToWriteWindow(windowSizeIncrement);
    }

    @Override
    public void priority(int streamId, int streamDependency, int weight, boolean exclusive) {
    }

    @Override
    public void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders) {
        throw new AssertionError("pushPromise");
    }

    @Override
    public void alternateService(int streamId, String origin, ByteString protocol, String host, int port, long maxAge) {
    }

    @Override
    public void error(Exception e) {
        socket.close();
        for (Iterator<Map.Entry<Integer, SpdySocket>> i = sockets.entrySet().iterator(); i.hasNext();) {
            Map.Entry<Integer, SpdySocket> entry = i.next();
            Util.end(entry.getValue(), e);
            i.remove();
        }
    }
}
