//
// HybiParser.java: draft-ietf-hybi-thewebsocketprotocol-13 parser
//
// Based on code from the faye project.
// https://github.com/faye/faye-websocket-node
// Copyright (c) 2009-2012 James Coglan
//
// Ported from Javascript to Java by Eric Butler <eric@codebutler.com>
//
// (The MIT License)
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.koushikdutta.async.http;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataEmitterReader;
import com.koushikdutta.async.callback.DataCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

abstract class HybiParser {
    private static final String TAG = "HybiParser";

    private boolean mMasking = true;
    private boolean mDeflate = false;

    private int     mStage;

    private boolean mFinal;
    private boolean mMasked;
    private boolean mDeflated;
    private int     mOpcode;
    private int     mLengthSize;
    private int     mLength;
    private int     mMode;

    private byte[] mMask    = new byte[0];
    private byte[] mPayload = new byte[0];

    private boolean mClosed = false;

    private ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();
    private Inflater mInflater = new Inflater(true);
    private byte[] mInflateBuffer = new byte[4096];

    private static final int BYTE   = 255;
    private static final int FIN    = 128;
    private static final int MASK   = 128;
    private static final int RSV1   =  64;
    private static final int RSV2   =  32;
    private static final int RSV3   =  16;
    private static final int OPCODE =  15;
    private static final int LENGTH = 127;

    private static final int MODE_TEXT   = 1;
    private static final int MODE_BINARY = 2;

    private static final int OP_CONTINUATION =  0;
    private static final int OP_TEXT         =  1;
    private static final int OP_BINARY       =  2;
    private static final int OP_CLOSE        =  8;
    private static final int OP_PING         =  9;
    private static final int OP_PONG         = 10;

    private static final List<Integer> OPCODES = Arrays.asList(
        OP_CONTINUATION,
        OP_TEXT,
        OP_BINARY,
        OP_CLOSE,
        OP_PING,
        OP_PONG
    );

    private static final List<Integer> FRAGMENTED_OPCODES = Arrays.asList(
        OP_CONTINUATION, OP_TEXT, OP_BINARY
    );
//
//    public HybiParser(WebSocketClient client) {
//        mClient = client;
//    }

    private static byte[] mask(byte[] payload, byte[] mask, int offset) {
        if (mask.length == 0) return payload;

        for (int i = 0; i < payload.length - offset; i++) {
            payload[offset + i] = (byte) (payload[offset + i] ^ mask[i % 4]);
        }
        return payload;
    }

    private byte[] inflate(byte[] payload) throws DataFormatException {
        ByteArrayOutputStream inflated = new ByteArrayOutputStream();

        mInflater.setInput(payload);
        while (!mInflater.needsInput()) {
            int chunkSize = mInflater.inflate(mInflateBuffer);
            inflated.write(mInflateBuffer, 0, chunkSize);
        }

        mInflater.setInput(new byte[] { 0, 0, -1, -1 });
        while (!mInflater.needsInput()) {
            int chunkSize = mInflater.inflate(mInflateBuffer);
            inflated.write(mInflateBuffer, 0, chunkSize);
        }

        return inflated.toByteArray();
    }

    public void setMasking(boolean masking) {
        mMasking = masking;
    }

    public void setDeflate(boolean deflate) {
        mDeflate = deflate;
    }

    DataCallback mStage0 = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            try {
                parseOpcode(bb.get());
            }
            catch (ProtocolError e) {
                report(e);
                e.printStackTrace();
            }
            parse();
        }
    };
    
    DataCallback mStage1 = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            parseLength(bb.get());
            parse();
        }
    };
    
    DataCallback mStage2 = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            byte[] bytes = new byte[mLengthSize];
            bb.get(bytes);
            try {
                parseExtendedLength(bytes);
            }
            catch (ProtocolError e) {
                report(e);
                e.printStackTrace();
            }
            parse();
        }
    };
    
    DataCallback mStage3 = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            mMask = new byte[4];
            bb.get(mMask);
            mStage = 4;
            parse();
        }
    };

    DataCallback mStage4 = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            assert bb.remaining() == mLength;
            mPayload = new byte[mLength];
            bb.get(mPayload);
            try {
                emitFrame();
            }
            catch (IOException e) {
                report(e);
                e.printStackTrace();
            }
            mStage = 0;
            parse();
        }
    };
    
    void parse() {
        switch (mStage) {
        case 0:
            mReader.read(1, mStage0);
            break;
        case 1:
            mReader.read(1, mStage1);
            break;
        case 2:
            mReader.read(mLengthSize, mStage2);
            break;
        case 3:
            mReader.read(4, mStage3);
            break;
        case 4:
            mReader.read(mLength, mStage4);
            break;
        }
    }
    
    private DataEmitterReader mReader = new DataEmitterReader();

	private static final long BASE = 2;

	private static final long _2_TO_8_ = BASE << 7;

	private static final long _2_TO_16_ = BASE << 15;

	private static final long _2_TO_24 = BASE << 23;

	private static final long _2_TO_32_ = BASE << 31;

	private static final long _2_TO_40_ = BASE << 39;

	private static final long _2_TO_48_ = BASE << 47;

	private static final long _2_TO_56_ = BASE << 55;
    public HybiParser(DataEmitter socket) {
        socket.setDataCallback(mReader);
        parse();
    }

    private void parseOpcode(byte data) throws ProtocolError {
        boolean rsv1 = (data & RSV1) == RSV1;
        boolean rsv2 = (data & RSV2) == RSV2;
        boolean rsv3 = (data & RSV3) == RSV3;

        if ((!mDeflate && rsv1) || rsv2 || rsv3) {
            throw new ProtocolError("RSV not zero");
        }

        mFinal   = (data & FIN) == FIN;
        mOpcode  = (data & OPCODE);
        mDeflated = rsv1;
        mMask    = new byte[0];
        mPayload = new byte[0];

        if (!OPCODES.contains(mOpcode)) {
            throw new ProtocolError("Bad opcode");
        }

        if (!FRAGMENTED_OPCODES.contains(mOpcode) && !mFinal) {
            throw new ProtocolError("Expected non-final packet");
        }

        mStage = 1;
    }

    private void parseLength(byte data) {
        mMasked = (data & MASK) == MASK;
        mLength = (data & LENGTH);

        if (mLength >= 0 && mLength <= 125) {
            mStage = mMasked ? 3 : 4;
        } else {
            mLengthSize = (mLength == 126) ? 2 : 8;
            mStage      = 2;
        }
    }

    private void parseExtendedLength(byte[] buffer) throws ProtocolError {
        mLength = getInteger(buffer);
        mStage  = mMasked ? 3 : 4;
    }

    public byte[] frame(String data) {
        return frame(OP_TEXT, data, -1);
    }

    public byte[] frame(byte[] data) {
        return frame(OP_BINARY, data, -1);
    }
    
    public byte[] frame(byte[] data, int offset, int length) {
    	return frame(OP_BINARY, data, -1, offset, length);
    }

    public byte[] pingFrame(String data) {
        return frame(OP_PING, data, -1);
    }

    /**
     * Flip the opcode so to avoid the name collision with the public method
     * 
     * @param opcode
     * @param data
     * @param errorCode
     * @return
     */
    private byte[] frame(int opcode, byte[] data, int errorCode)  {
        return frame(opcode, data, errorCode, 0, data.length);
    }

    /**
     * Don't actually need the flipped method signature, trying to keep it in line with the byte[] version
     * 
     * @param opcode
     * @param data
     * @param errorCode
     * @return
     */
    private byte[] frame(int opcode, String data, int errorCode) {
        return frame(opcode, decode(data), errorCode);
    }
    
    private byte[] frame(int opcode, byte [] data, int errorCode, int dataOffset, int dataLength) {
        if (mClosed) return null;

//        Log.d(TAG, "Creating frame for: " + data + " op: " + opcode + " err: " + errorCode);
        byte[] buffer = data;
        int insert = (errorCode > 0) ? 2 : 0;
        int length = dataLength + insert - dataOffset;
        int header = (length <= 125) ? 2 : (length <= 65535 ? 4 : 10);
        int offset = header + (mMasking ? 4 : 0);
        int masked = mMasking ? MASK : 0;
        byte[] frame = new byte[length + offset];

        frame[0] = (byte) ((byte)FIN | (byte)opcode);

        if (length <= 125) {
            frame[1] = (byte) (masked | length);
        } else if (length <= 65535) {
            frame[1] = (byte) (masked | 126);
            frame[2] = (byte) (length / 256);
            frame[3] = (byte) (length & BYTE);
        } else {
        	
        	frame[1] = (byte) (masked | 127);
            frame[2] = (byte) (( length / _2_TO_56_) & BYTE);
            frame[3] = (byte) (( length / _2_TO_48_) & BYTE);
            frame[4] = (byte) (( length / _2_TO_40_) & BYTE);
            frame[5] = (byte) (( length / _2_TO_32_) & BYTE);
            frame[6] = (byte) (( length / _2_TO_24) & BYTE);
            frame[7] = (byte) (( length / _2_TO_16_) & BYTE);
            frame[8] = (byte) (( length / _2_TO_8_)  & BYTE);
            frame[9] = (byte) (length & BYTE);
        }

        if (errorCode > 0) {
            frame[offset] = (byte) ((errorCode / 256) & BYTE);
            frame[offset+1] = (byte) (errorCode & BYTE);
        }
        
        System.arraycopy(buffer, dataOffset, frame, offset + insert, dataLength - dataOffset);

        if (mMasking) {
            byte[] mask = {
                (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256),
                (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256)
            };
            System.arraycopy(mask, 0, frame, header, mask.length);
            mask(frame, mask, offset);
        }

        return frame;
    }

    public void close(int code, String reason) {
        if (mClosed) return;
        sendFrame(frame(OP_CLOSE, reason, code));
        mClosed = true;
    }

    private void emitFrame() throws IOException {
        byte[] payload = mask(mPayload, mMask, 0);
        if (mDeflated) {
            try {
                payload = inflate(payload);
            } catch (DataFormatException e) {
                throw new IOException("Invalid deflated data");
            }
        }
        int opcode = mOpcode;

        if (opcode == OP_CONTINUATION) {
            if (mMode == 0) {
                throw new ProtocolError("Mode was not set.");
            }
            mBuffer.write(payload);
            if (mFinal) {
                byte[] message = mBuffer.toByteArray();
                if (mMode == MODE_TEXT) {
                    onMessage(encode(message));
                } else {
                    onMessage(message);
                }
                reset();
            }

        } else if (opcode == OP_TEXT) {
            if (mFinal) {
                String messageText = encode(payload);
                onMessage(messageText);
            } else {
                mMode = MODE_TEXT;
                mBuffer.write(payload);
            }

        } else if (opcode == OP_BINARY) {
            if (mFinal) {
                onMessage(payload);
            } else {
                mMode = MODE_BINARY;
                mBuffer.write(payload);
            }

        } else if (opcode == OP_CLOSE) {
            int    code   = (payload.length >= 2) ? 256 * (payload[0] & 0xFF) + (payload[1] & 0xFF) : 0;
            String reason = (payload.length >  2) ? encode(slice(payload, 2))     : null;
//            Log.d(TAG, "Got close op! " + code + " " + reason);
            onDisconnect(code, reason);

        } else if (opcode == OP_PING) {
            if (payload.length > 125) { throw new ProtocolError("Ping payload too large"); }
//            Log.d(TAG, "Sending pong!!");
            String message = encode(payload);
            sendFrame(frame(OP_PONG, payload, -1));
            onPing(message);
        } else if (opcode == OP_PONG) {
            String message = encode(payload);
            onPong(message);
//            Log.d(TAG, "Got pong! " + message);
        }
    }
    
    protected abstract void onMessage(byte[] payload);
    protected abstract void onMessage(String payload);
    protected abstract void onPong(String payload);
    protected abstract void onPing(String payload);
    protected abstract void onDisconnect(int code, String reason);
    protected abstract void report(Exception ex);

    protected abstract void sendFrame(byte[] frame);

    private void reset() {
        mMode = 0;
        mBuffer.reset();
    }

    private String encode(byte[] buffer) {
        try {
            return new String(buffer, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] decode(String string) {
        try {
            return (string).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private int getInteger(byte[] bytes) throws ProtocolError {
        long i = byteArrayToLong(bytes, 0, bytes.length);
        if (i < 0 || i > Integer.MAX_VALUE) {
            throw new ProtocolError("Bad integer: " + i);
        }
        return (int) i;
    }

    private byte[] slice(byte[] array, int start) {
        byte[] copy = new byte[array.length - start];
        System.arraycopy(array, start, copy, 0, array.length - start);
        return copy;
    }

    public static class ProtocolError extends IOException {
        public ProtocolError(String detailMessage) {
            super(detailMessage);
        }
    }

    private static long byteArrayToLong(byte[] b, int offset, int length) {
        if (b.length < length)
            throw new IllegalArgumentException("length must be less than or equal to b.length");

        long value = 0;
        for (int i = 0; i < length; i++) {
            int shift = (length - 1 - i) * 8;
            value += (b[i + offset] & 0x000000FF) << shift;
        }
        return value;
    }

}
