package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Created by koush on 7/27/14.
 */
class HeaderReader {
    Inflater inflater;
    public HeaderReader() {
        inflater = new Inflater() {
            @Override public int inflate(byte[] buffer, int offset, int count)
            throws DataFormatException {
                int result = super.inflate(buffer, offset, count);
                if (result == 0 && needsDictionary()) {
                    setDictionary(Spdy3.DICTIONARY);
                    result = super.inflate(buffer, offset, count);
                }
                return result;
            }
        };
    }

    public List<Header> readHeader(ByteBufferList bb, int length) throws IOException {
        byte[] bytes = new byte[length];
        bb.get(bytes);

        inflater.setInput(bytes);

        ByteBufferList source = new ByteBufferList().order(ByteOrder.BIG_ENDIAN);
        while (!inflater.needsInput()) {
            ByteBuffer b = ByteBufferList.obtain(8192);
            try {
                int read = inflater.inflate(b.array());
                b.limit(read);
                source.add(b);
            }
            catch (DataFormatException e) {
                throw new IOException(e);
            }
        }

        int numberOfPairs = source.getInt();
        List<Header> entries = new ArrayList<Header>(numberOfPairs);
        for (int i = 0; i < numberOfPairs; i++) {
            ByteString name = readByteString(source).toAsciiLowercase();
            ByteString values = readByteString(source);
            if (name.size() == 0) throw new IOException("name.size == 0");
            entries.add(new Header(name, values));
        }
        return entries;
    }

    private static ByteString readByteString(ByteBufferList source) {
        int length = source.getInt();
        return ByteString.of(source.getBytes(length));
    }
}
