package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.PushParser;
import com.koushikdutta.async.PushParser.ParseCallback;
import com.koushikdutta.async.callback.DataCallback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

public class GZIPInputFilter extends InflaterInputFilter {
    static short peekShort(byte[] src, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) ((src[offset] << 8) | (src[offset + 1] & 0xff));
        } else {
            return (short) ((src[offset + 1] << 8) | (src[offset] & 0xff));
        }
    }

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
            parser.readByteArray(10, new ParseCallback<byte[]>() {
                int flags;
                boolean hcrc;

                public void parsed(byte[] header) {
                    short magic = peekShort(header, 0, ByteOrder.LITTLE_ENDIAN);
                    if (magic != (short) GZIPInputStream.GZIP_MAGIC) {
                        report(new IOException(String.format(Locale.ENGLISH, "unknown format (magic number %x)", magic)));
                        emitter.setDataCallback(new NullDataCallback());
                        return;
                    }
                    flags = header[3];
                    hcrc = (flags & FHCRC) != 0;
                    if (hcrc) {
                        crc.update(header, 0, header.length);
                    }
                    if ((flags & FEXTRA) != 0) {
                        parser.readByteArray(2, new ParseCallback<byte[]>() {
                            public void parsed(byte[] header) {
                                if (hcrc) {
                                    crc.update(header, 0, 2);
                                }
                                int length = peekShort(header, 0, ByteOrder.LITTLE_ENDIAN) & 0xffff;
                                parser.readByteArray(length, new ParseCallback<byte[]>() {
                                    public void parsed(byte[] buf) {
                                        if (hcrc) {
                                            crc.update(buf, 0, buf.length);
                                        }
                                        next();
                                    }
                                });
                            }
                        });
                    } else {
                        next();
                    }
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
                            bb.recycle();
                            done();
                        }
                    };
                    if ((flags & FNAME) != 0) {
                        parser.until((byte) 0, summer);
                        return;
                    }
                    if ((flags & FCOMMENT) != 0) {
                        parser.until((byte) 0, summer);
                        return;
                    }

                    done();
                }

                private void done() {
                    if (hcrc) {
                        parser.readByteArray(2, new ParseCallback<byte[]>() {
                            public void parsed(byte[] header) {
                                short crc16 = peekShort(header, 0, ByteOrder.LITTLE_ENDIAN);
                                if ((short) crc.getValue() != crc16) {
                                    report(new IOException("CRC mismatch"));
                                    return;
                                }
                                crc.reset();
                                mNeedsHeader = false;
                                setDataEmitter(emitter);
//                            emitter.setDataCallback(GZIPInputFilter.this);
                            }
                        });
                    } else {
                        mNeedsHeader = false;
                        setDataEmitter(emitter);
                    }
                }
            });
        }
        else {
            super.onDataAvailable(emitter, bb);
        }
    }
}
