/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.koushikdutta.async.http.spdy;

import android.os.Build;

import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.BuildConfig;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataEmitterReader;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.util.Charsets;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;


/**
 * Read and write spdy/3.1 frames.
 * http://www.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3-1
 */
final class Spdy3 implements Variant {

    @Override
    public Protocol getProtocol() {
        return Protocol.SPDY_3;
    }

    static final int TYPE_DATA = 0x0;
    static final int TYPE_SYN_STREAM = 0x1;
    static final int TYPE_SYN_REPLY = 0x2;
    static final int TYPE_RST_STREAM = 0x3;
    static final int TYPE_SETTINGS = 0x4;
    static final int TYPE_PING = 0x6;
    static final int TYPE_GOAWAY = 0x7;
    static final int TYPE_HEADERS = 0x8;
    static final int TYPE_WINDOW_UPDATE = 0x9;

    static final int FLAG_FIN = 0x1;
    static final int FLAG_UNIDIRECTIONAL = 0x2;

    static final int VERSION = 3;

    static final byte[] DICTIONARY;

    static {
        try {
            DICTIONARY = ("\u0000\u0000\u0000\u0007options\u0000\u0000\u0000\u0004hea"
            + "d\u0000\u0000\u0000\u0004post\u0000\u0000\u0000\u0003put\u0000\u0000\u0000\u0006dele"
            + "te\u0000\u0000\u0000\u0005trace\u0000\u0000\u0000\u0006accept\u0000\u0000\u0000"
            + "\u000Eaccept-charset\u0000\u0000\u0000\u000Faccept-encoding\u0000\u0000\u0000\u000Fa"
            + "ccept-language\u0000\u0000\u0000\raccept-ranges\u0000\u0000\u0000\u0003age\u0000"
            + "\u0000\u0000\u0005allow\u0000\u0000\u0000\rauthorization\u0000\u0000\u0000\rcache-co"
            + "ntrol\u0000\u0000\u0000\nconnection\u0000\u0000\u0000\fcontent-base\u0000\u0000"
            + "\u0000\u0010content-encoding\u0000\u0000\u0000\u0010content-language\u0000\u0000"
            + "\u0000\u000Econtent-length\u0000\u0000\u0000\u0010content-location\u0000\u0000\u0000"
            + "\u000Bcontent-md5\u0000\u0000\u0000\rcontent-range\u0000\u0000\u0000\fcontent-type"
            + "\u0000\u0000\u0000\u0004date\u0000\u0000\u0000\u0004etag\u0000\u0000\u0000\u0006expe"
            + "ct\u0000\u0000\u0000\u0007expires\u0000\u0000\u0000\u0004from\u0000\u0000\u0000"
            + "\u0004host\u0000\u0000\u0000\bif-match\u0000\u0000\u0000\u0011if-modified-since"
            + "\u0000\u0000\u0000\rif-none-match\u0000\u0000\u0000\bif-range\u0000\u0000\u0000"
            + "\u0013if-unmodified-since\u0000\u0000\u0000\rlast-modified\u0000\u0000\u0000\blocati"
            + "on\u0000\u0000\u0000\fmax-forwards\u0000\u0000\u0000\u0006pragma\u0000\u0000\u0000"
            + "\u0012proxy-authenticate\u0000\u0000\u0000\u0013proxy-authorization\u0000\u0000"
            + "\u0000\u0005range\u0000\u0000\u0000\u0007referer\u0000\u0000\u0000\u000Bretry-after"
            + "\u0000\u0000\u0000\u0006server\u0000\u0000\u0000\u0002te\u0000\u0000\u0000\u0007trai"
            + "ler\u0000\u0000\u0000\u0011transfer-encoding\u0000\u0000\u0000\u0007upgrade\u0000"
            + "\u0000\u0000\nuser-agent\u0000\u0000\u0000\u0004vary\u0000\u0000\u0000\u0003via"
            + "\u0000\u0000\u0000\u0007warning\u0000\u0000\u0000\u0010www-authenticate\u0000\u0000"
            + "\u0000\u0006method\u0000\u0000\u0000\u0003get\u0000\u0000\u0000\u0006status\u0000"
            + "\u0000\u0000\u0006200 OK\u0000\u0000\u0000\u0007version\u0000\u0000\u0000\bHTTP/1.1"
            + "\u0000\u0000\u0000\u0003url\u0000\u0000\u0000\u0006public\u0000\u0000\u0000\nset-coo"
            + "kie\u0000\u0000\u0000\nkeep-alive\u0000\u0000\u0000\u0006origin100101201202205206300"
            + "302303304305306307402405406407408409410411412413414415416417502504505203 Non-Authori"
            + "tative Information204 No Content301 Moved Permanently400 Bad Request401 Unauthorized"
            + "403 Forbidden404 Not Found500 Internal Server Error501 Not Implemented503 Service Un"
            + "availableJan Feb Mar Apr May Jun Jul Aug Sept Oct Nov Dec 00:00:00 Mon, Tue, Wed, Th"
            + "u, Fri, Sat, Sun, GMTchunked,text/html,image/png,image/jpg,image/gif,application/xml"
            + ",application/xhtml+xml,text/plain,text/javascript,publicprivatemax-age=gzip,deflate,"
            + "sdchcharset=utf-8charset=iso-8859-1,utf-,*,enq=0.").getBytes(Charsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError();
        }
    }

    @Override
    public FrameReader newReader(DataEmitter source, FrameReader.Handler handler, boolean client) {
        return new Reader(source, handler, client);
    }

    @Override
    public FrameWriter newWriter(BufferedDataSink sink, boolean client) {
        return new Writer(sink, client);
    }

    @Override
    public int maxFrameSize() {
        return 16383;
    }

    /**
     * Read spdy/3 frames.
     */
    static final class Reader implements FrameReader {
        private final HeaderReader headerReader = new HeaderReader();
        private final DataEmitter emitter;
        private final boolean client;
        private final Handler handler;
        private final DataEmitterReader reader;

        Reader(DataEmitter emitter, Handler handler, boolean client) {
            this.emitter = emitter;
            this.handler = handler;
            this.client = client;

            emitter.setEndCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    // TODO: handle termination
                }
            });

            reader = new DataEmitterReader();
            parseFrameHeader();
        }

        private void parseFrameHeader() {
            emitter.setDataCallback(reader);
            reader.read(8, onFrame);
        }

        int w1;
        int w2;
        int flags;
        int length;
        int streamId;
        boolean inFinished;
        private final ByteBufferList emptyList = new ByteBufferList();
        private final DataCallback onFrame = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                bb.order(ByteOrder.BIG_ENDIAN);
                w1 = bb.getInt();
                w2 = bb.getInt();

                boolean control = (w1 & 0x80000000) != 0;
                flags = (w2 & 0xff000000) >>> 24;
                length = (w2 & 0xffffff);

                if (!control) {
                    streamId = w1 & 0x7fffffff;
                    inFinished = (flags & FLAG_FIN) != 0;
                    emitter.setDataCallback(onDataFrame);

                    if (length == 0) {
                        // zero length packet, immediately trigger the data parsing
                        // fixes the hanging response portion of https://github.com/koush/ion/issues/443#issuecomment-67729152
                        onDataFrame.onDataAvailable(emitter, emptyList);
                    }
                }
                else {
                    reader.read(length, onFullFrame);
                }
            }
        };

        ByteBufferList partial = new ByteBufferList();
        private final DataCallback onDataFrame = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                int toRead = Math.min(bb.remaining(), length);
                if (toRead < bb.remaining()) {
                    bb.get(partial, toRead);
                    bb = partial;
                }

                length -= toRead;
                handler.data(length == 0 && inFinished, streamId, bb);

                if (length == 0)
                    parseFrameHeader();
            }
        };

        private final DataCallback onFullFrame = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                // queue up the next frame read
                bb.order(ByteOrder.BIG_ENDIAN);

                int version = (w1 & 0x7fff0000) >>> 16;
                int type = (w1 & 0xffff);

                try {
                    if (version != 3) {
                        throw new ProtocolException("version != 3: " + version);
                    }

                    switch (type) {
                        case TYPE_SYN_STREAM:
                            readSynStream(bb, flags, length);
                            break;

                        case TYPE_SYN_REPLY:
                            readSynReply(bb, flags, length);
                            break;

                        case TYPE_RST_STREAM:
                            readRstStream(bb, flags, length);
                            break;

                        case TYPE_SETTINGS:
                            readSettings(bb, flags, length);
                            break;

                        case TYPE_PING:
                            readPing(bb, flags, length);
                            break;

                        case TYPE_GOAWAY:
                            readGoAway(bb, flags, length);
                            break;

                        case TYPE_HEADERS:
                            readHeaders(bb, flags, length);
                            break;

                        case TYPE_WINDOW_UPDATE:
                            readWindowUpdate(bb, flags, length);
                            break;

                        default:
                            bb.recycle();
                            break;
                    }
                    parseFrameHeader();
                }
                catch (IOException e) {
                    handler.error(e);
                }
            }
        };

        /*
        @Override
        public void readConnectionPreface() {
        }
        */

        private void readSynStream(ByteBufferList source, int flags, int length) throws IOException {
            int w1 = source.getInt();
            int w2 = source.getInt();
            int streamId = w1 & 0x7fffffff;
            int associatedStreamId = w2 & 0x7fffffff;
            source.getShort(); // int priority = (s3 & 0xe000) >>> 13; int slot = s3 & 0xff;
            List<Header> headerBlock = headerReader.readHeader(source, length - 10);

            boolean inFinished = (flags & FLAG_FIN) != 0;
            boolean outFinished = (flags & FLAG_UNIDIRECTIONAL) != 0;
            handler.headers(outFinished, inFinished, streamId, associatedStreamId, headerBlock,
            HeadersMode.SPDY_SYN_STREAM);
        }

        private void readSynReply(ByteBufferList source, int flags, int length) throws IOException {
            int w1 = source.getInt();
            int streamId = w1 & 0x7fffffff;
            List<Header> headerBlock = headerReader.readHeader(source, length - 4);
            boolean inFinished = (flags & FLAG_FIN) != 0;
            handler.headers(false, inFinished, streamId, -1, headerBlock, HeadersMode.SPDY_REPLY);
        }

        private void readRstStream(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 8) throw ioException("TYPE_RST_STREAM length: %d != 8", length);
            int streamId = source.getInt() & 0x7fffffff;
            int errorCodeInt = source.getInt();
            ErrorCode errorCode = ErrorCode.fromSpdy3Rst(errorCodeInt);
            if (errorCode == null) {
                throw ioException("TYPE_RST_STREAM unexpected error code: %d", errorCodeInt);
            }
            handler.rstStream(streamId, errorCode);
        }

        private void readHeaders(ByteBufferList source, int flags, int length) throws IOException {
            int w1 = source.getInt();
            int streamId = w1 & 0x7fffffff;
            List<Header> headerBlock = headerReader.readHeader(source, length - 4);
            handler.headers(false, false, streamId, -1, headerBlock, HeadersMode.SPDY_HEADERS);
        }

        private void readWindowUpdate(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 8) throw ioException("TYPE_WINDOW_UPDATE length: %d != 8", length);
            int w1 = source.getInt();
            int w2 = source.getInt();
            int streamId = w1 & 0x7fffffff;
            long increment = w2 & 0x7fffffff;
            if (increment == 0) throw ioException("windowSizeIncrement was 0", increment);
            handler.windowUpdate(streamId, increment);
        }

        private void readPing(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 4) throw ioException("TYPE_PING length: %d != 4", length);
            int id = source.getInt();
            boolean ack = client == ((id & 1) == 1);
            handler.ping(ack, id, 0);
        }

        private void readGoAway(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 8) throw ioException("TYPE_GOAWAY length: %d != 8", length);
            int lastGoodStreamId = source.getInt() & 0x7fffffff;
            int errorCodeInt = source.getInt();
            ErrorCode errorCode = ErrorCode.fromSpdyGoAway(errorCodeInt);
            if (errorCode == null) {
                throw ioException("TYPE_GOAWAY unexpected error code: %d", errorCodeInt);
            }
            handler.goAway(lastGoodStreamId, errorCode, ByteString.EMPTY);
        }

        private void readSettings(ByteBufferList source, int flags, int length) throws IOException {
            int numberOfEntries = source.getInt();
            if (length != 4 + 8 * numberOfEntries) {
                throw ioException("TYPE_SETTINGS length: %d != 4 + 8 * %d", length, numberOfEntries);
            }
            Settings settings = new Settings();
            for (int i = 0; i < numberOfEntries; i++) {
                int w1 = source.getInt();
                int value = source.getInt();
                int idFlags = (w1 & 0xff000000) >>> 24;
                int id = w1 & 0xffffff;
                settings.set(id, idFlags, value);
            }
            boolean clearPrevious = (flags & Settings.FLAG_CLEAR_PREVIOUSLY_PERSISTED_SETTINGS) != 0;
            handler.settings(clearPrevious, settings);
        }

        private static IOException ioException(String message, Object... args) throws IOException {
            throw new IOException(String.format(Locale.ENGLISH, message, args));
        }
    }

    /**
     * Write spdy/3 frames.
     */
    static final class Writer implements FrameWriter {
        private final BufferedDataSink sink;
        private final boolean client;
        private boolean closed;
        private ByteBufferList frameHeader = new ByteBufferList();
        private final Deflater deflater = new Deflater();

        Writer(BufferedDataSink sink, boolean client) {
            this.sink = sink;
            this.client = client;

            deflater.setDictionary(DICTIONARY);
        }

        @Override
        public void ackSettings() {
            // Do nothing: no ACK for SPDY/3 settings.
        }

        @Override
        public void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders)
        throws IOException {
            // Do nothing: no push promise for SPDY/3.
        }

        @Override
        public synchronized void connectionPreface() {
            // Do nothing: no connection preface for SPDY/3.
        }

        @Override
        public synchronized void synStream(boolean outFinished, boolean inFinished,
                                           int streamId, int associatedStreamId, List<Header> headerBlock)
        throws IOException {
            if (closed) throw new IOException("closed");
            ByteBufferList headerBlockBuffer = writeNameValueBlockToBuffer(headerBlock);
            int length = (int) (10 + headerBlockBuffer.remaining());
            int type = TYPE_SYN_STREAM;
            int flags = (outFinished ? FLAG_FIN : 0) | (inFinished ? FLAG_UNIDIRECTIONAL : 0);

            int unused = 0;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(streamId & 0x7fffffff);
            sink.putInt(associatedStreamId & 0x7fffffff);
            sink.putShort((short) ((unused & 0x7) << 13 | (unused & 0x1f) << 8 | (unused & 0xff)));
            sink.flip();
            this.sink.write(frameHeader.add(sink).add(headerBlockBuffer));
        }

        @Override
        public synchronized void synReply(boolean outFinished, int streamId,
                                          List<Header> headerBlock) throws IOException {
            if (closed) throw new IOException("closed");
            ByteBufferList headerBlockBuffer = writeNameValueBlockToBuffer(headerBlock);
            int type = TYPE_SYN_REPLY;
            int flags = (outFinished ? FLAG_FIN : 0);
            int length = (int) (headerBlockBuffer.remaining() + 4);

            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(streamId & 0x7fffffff);
            sink.flip();
            this.sink.write(frameHeader.add(sink).add(headerBlockBuffer));
        }

        @Override
        public synchronized void headers(int streamId, List<Header> headerBlock)
        throws IOException {
            if (closed) throw new IOException("closed");
            ByteBufferList headerBlockBuffer = writeNameValueBlockToBuffer(headerBlock);
            int flags = 0;
            int type = TYPE_HEADERS;
            int length = (int) (headerBlockBuffer.remaining() + 4);

            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(streamId & 0x7fffffff);
            sink.flip();
            this.sink.write(frameHeader.add(sink).add(headerBlockBuffer));
        }

        @Override
        public synchronized void rstStream(int streamId, ErrorCode errorCode)
        throws IOException {
            if (closed) throw new IOException("closed");
            if (errorCode.spdyRstCode == -1) throw new IllegalArgumentException();
            int flags = 0;
            int type = TYPE_RST_STREAM;
            int length = 8;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(streamId & 0x7fffffff);
            sink.putInt(errorCode.spdyRstCode);
            sink.flip();
            this.sink.write(frameHeader.addAll(sink));
        }

        @Override
        public synchronized void data(boolean outFinished, int streamId, ByteBufferList source) throws IOException {
            int flags = (outFinished ? FLAG_FIN : 0);
            sendDataFrame(streamId, flags, source);
        }

        ByteBufferList dataList = new ByteBufferList();
        void sendDataFrame(int streamId, int flags, ByteBufferList buffer)
        throws IOException {
            if (closed) throw new IOException("closed");
            int byteCount = buffer.remaining();
            if (byteCount > 0xffffffL) {
                throw new IllegalArgumentException("FRAME_TOO_LARGE max size is 16Mib: " + byteCount);
            }
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(streamId & 0x7fffffff);
            sink.putInt((flags & 0xff) << 24 | byteCount & 0xffffff);
            sink.flip();
            dataList.add(sink).add(buffer);
            this.sink.write(dataList);
        }

        ByteBufferList headerBlockList = new ByteBufferList();
        private ByteBufferList writeNameValueBlockToBuffer(List<Header> headerBlock) throws IOException {
            if (headerBlockList.hasRemaining()) throw new IllegalStateException();
            ByteBuffer headerBlockOut = ByteBufferList.obtain(8192).order(ByteOrder.BIG_ENDIAN);
            headerBlockOut.putInt(headerBlock.size());
            for (int i = 0, size = headerBlock.size(); i < size; i++) {
                ByteString name = headerBlock.get(i).name;
                headerBlockOut.putInt(name.size());
                headerBlockOut.put(name.toByteArray());
                ByteString value = headerBlock.get(i).value;
                headerBlockOut.putInt(value.size());
                headerBlockOut.put(value.toByteArray());
                if (headerBlockOut.remaining() < headerBlockOut.capacity() / 2) {
                    ByteBuffer newOut = ByteBufferList.obtain(headerBlockOut.capacity() * 2).order(ByteOrder.BIG_ENDIAN);
                    headerBlockOut.flip();
                    newOut.put(headerBlockOut);
                    ByteBufferList.reclaim(headerBlockOut);
                    headerBlockOut = newOut;
                }
            }

            headerBlockOut.flip();
            deflater.setInput(headerBlockOut.array(), 0, headerBlockOut.remaining());
            while (!deflater.needsInput()) {
                ByteBuffer deflated = ByteBufferList.obtain(headerBlockOut.capacity()).order(ByteOrder.BIG_ENDIAN);
                final int read;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    read = deflater.deflate(deflated.array(), 0, deflated.capacity(), Deflater.SYNC_FLUSH);
                } else {
                    read = deflater.deflate(deflated.array(), 0, deflated.capacity());
                }

                deflated.limit(read);
                headerBlockList.add(deflated);
            }
            ByteBufferList.reclaim(headerBlockOut);

            return headerBlockList;
        }

        @Override
        public synchronized void settings(Settings settings) throws IOException {
            if (closed) throw new IOException("closed");
            int type = TYPE_SETTINGS;
            int flags = 0;
            int size = settings.size();
            int length = 4 + size * 8;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(size);
            for (int i = 0; i <= Settings.COUNT; i++) {
                if (!settings.isSet(i)) continue;
                int settingsFlags = settings.flags(i);
                sink.putInt((settingsFlags & 0xff) << 24 | (i & 0xffffff));
                sink.putInt(settings.get(i));
            }
            sink.flip();
            this.sink.write(frameHeader.addAll(sink));
        }

        @Override
        public synchronized void ping(boolean reply, int payload1, int payload2)
        throws IOException {
            if (closed) throw new IOException("closed");
            boolean payloadIsReply = client != ((payload1 & 1) == 1);
            if (reply != payloadIsReply) throw new IllegalArgumentException("payload != reply");
            int type = TYPE_PING;
            int flags = 0;
            int length = 4;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(payload1);
            sink.flip();
            this.sink.write(frameHeader.addAll(sink));
        }

        @Override
        public synchronized void goAway(int lastGoodStreamId, ErrorCode errorCode,
                                        byte[] ignored) throws IOException {
            if (closed) throw new IOException("closed");
            if (errorCode.spdyGoAwayCode == -1) {
                throw new IllegalArgumentException("errorCode.spdyGoAwayCode == -1");
            }
            int type = TYPE_GOAWAY;
            int flags = 0;
            int length = 8;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(lastGoodStreamId);
            sink.putInt(errorCode.spdyGoAwayCode);
            sink.flip();
            this.sink.write(frameHeader.addAll(sink));
        }

        @Override
        public synchronized void windowUpdate(int streamId, long increment)
        throws IOException {
            if (closed) throw new IOException("closed");
            if (increment == 0 || increment > 0x7fffffffL) {
                throw new IllegalArgumentException(
                "windowSizeIncrement must be between 1 and 0x7fffffff: " + increment);
            }
            int type = TYPE_WINDOW_UPDATE;
            int flags = 0;
            int length = 8;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(0x80000000 | (VERSION & 0x7fff) << 16 | type & 0xffff);
            sink.putInt((flags & 0xff) << 24 | length & 0xffffff);
            sink.putInt(streamId);
            sink.putInt((int) increment);
            sink.flip();
            this.sink.write(frameHeader.addAll(sink));
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
        }
    }
}
