package com.koushikdutta.async.http.filter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.PushParser;
import com.koushikdutta.async.TapCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.libcore.Memory;

public class GZIPInputFilter extends InflaterInputFilter {
    private static final int FCOMMENT = 16;

    private static final int FEXTRA = 4;

    private static final int FHCRC = 2;

    private static final int FNAME = 8;


    
    public GZIPInputFilter() {
        super(new Inflater(true));
    }
    
    boolean mNeedsHeader = true;
    protected CRC32 crc = new CRC32();

    public static int unsignedToBytes(byte b) {
        return b & 0xFF;
    }
    
    @Override
    @SuppressWarnings("unused")
    public void onDataAvailable(final DataEmitter emitter, ByteBufferList bb) {
        if (mNeedsHeader) {
            final PushParser parser = new PushParser(emitter);
            parser
            .readBuffer(10)
            .tap(new TapCallback() {
                int flags;
                boolean hcrc;
                public void tap(byte[] header) {
                    short magic = Memory.peekShort(header, 0, ByteOrder.LITTLE_ENDIAN);
                    if (magic != (short) GZIPInputStream.GZIP_MAGIC) {
                        report(new IOException(String.format("unknown format (magic number %x)", magic)));
                        emitter.setDataCallback(new NullDataCallback());
                        return;
                    }
                    flags = header[3];
                    hcrc = (flags & FHCRC) != 0;
                    if (hcrc) {
                        crc.update(header, 0, header.length);
                    }
                    if ((flags & FEXTRA) != 0) {
                        parser
                        .readBuffer(2)
                        .tap(new TapCallback() {
                            public void tap(byte[] header) {
                                if (hcrc) {
                                    crc.update(header, 0, 2);
                                }
                                int length = Memory.peekShort(header, 0, ByteOrder.LITTLE_ENDIAN) & 0xffff;
                                parser
                                .readBuffer(length)
                                .tap(new TapCallback() {
                                    public void tap(byte[] buf) {
                                        if (hcrc) {
                                            crc.update(buf, 0, buf.length);
                                        }
                                        next();
                                    }
                                });
                            }
                        });
                    }

                    next();
                }
                private void next() {
                    PushParser parser = new PushParser(emitter);
                    DataCallback summer = new DataCallback() {
                        @Override
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                            if (hcrc) {
                                while (bb.size() > 0) {
                                    ByteBuffer b = bb.remove();
                                    crc.update(b.array(), b.arrayOffset() + b.position(), b.remaining());
                                    ByteBufferList.reclaim(b);
                                }
                            }
                        }
                    };
                    if ((flags & FNAME) != 0) {
                        parser.until((byte)0, summer);
                    }
                    if ((flags & FCOMMENT) != 0) {
                        parser.until((byte)0, summer);
                    }
                    if (hcrc) {
                        parser.readBuffer(2);
                    }
                    else {
                        parser.noop();
                    }
                    parser.tap(new TapCallback() {
                        public void tap(byte[] header) {
                            if (header != null) {
                                short crc16 = Memory.peekShort(header, 0, ByteOrder.LITTLE_ENDIAN);
                                if ((short) crc.getValue() != crc16) {
                                    report(new IOException("CRC mismatch"));
                                    return;
                                }
                                crc.reset();
                            }
                            mNeedsHeader = false;
                            setDataEmitter(emitter);
//                            emitter.setDataCallback(GZIPInputFilter.this);
                        }
                    });
                }
            });
        }
        else {
            super.onDataAvailable(emitter, bb);
        }
    }
}
