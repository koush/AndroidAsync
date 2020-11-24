package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.Util;

public class ChunkedInputFilter extends FilteredDataEmitter {
    private int mChunkLength = 0;
    private int mChunkLengthRemaining = 0;
    private State mState = State.CHUNK_LEN;
    
    private enum State {
        CHUNK_LEN,
        CHUNK_LEN_CR,
        CHUNK_LEN_CRLF,
        CHUNK,
        CHUNK_CR,
        CHUNK_CRLF,
        COMPLETE,
        ERROR,
    }
    
    private boolean checkByte(char b, char value) {
        if (b != value) {
            mState = State.ERROR;
            report(new ChunkedDataException(value + " was expected, got " + (char)b));
            return false;
        }
        return true;
    }

    private boolean checkLF(char b) {
        return checkByte(b, '\n');
    }

    private boolean checkCR(char b) {
        return checkByte(b, '\r');
    }

    @Override
    protected void report(Exception e) {
        if (e == null && mState != State.COMPLETE)
            e = new ChunkedDataException("chunked input ended before final chunk");
        super.report(e);
    }

    ByteBufferList pending = new ByteBufferList();
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (mState == State.ERROR) {
            bb.recycle();
            return;
        }

        try {
            while (bb.remaining() > 0) {
                switch (mState) {
                case CHUNK_LEN:
                    char c = bb.getByteChar();
                    if (c == '\r') {
                        mState = State.CHUNK_LEN_CR;
                    }
                    else {
                        mChunkLength *= 16;
                        if (c >= 'a' && c <= 'f')
                            mChunkLength += (c - 'a' + 10);
                        else if (c >= '0' && c <= '9')
                            mChunkLength += c - '0';
                        else if (c >= 'A' && c <= 'F')
                            mChunkLength += (c - 'A' + 10);
                        else {
                            report(new ChunkedDataException("invalid chunk length: " + c));
                            return;
                        }
                    }
                    mChunkLengthRemaining = mChunkLength;
                    break;
                case CHUNK_LEN_CR:
                    if (!checkLF(bb.getByteChar()))
                        return;
                    mState = State.CHUNK;
                    break;
                case CHUNK:
                    int remaining = bb.remaining();
                    int reading = Math.min(mChunkLengthRemaining, remaining);
                    mChunkLengthRemaining -= reading;
                    if (mChunkLengthRemaining == 0) {
                        mState = State.CHUNK_CR;
                    }
                    if (reading == 0)
                        break;
                    bb.get(pending, reading);
                    Util.emitAllData(this, pending);
                    break;
                case CHUNK_CR:
                    if (!checkCR(bb.getByteChar()))
                        return;
                    mState = State.CHUNK_CRLF;
                    break;
                case CHUNK_CRLF:
                    if (!checkLF(bb.getByteChar()))
                        return;
                    if (mChunkLength > 0) {
                        mState = State.CHUNK_LEN;
                        
                    }
                    else {
                        mState = State.COMPLETE;
                        report(null);
                    }
                    mChunkLength = 0;
                    break;
                case COMPLETE:
//                    Exception fail = new Exception("Continued receiving data after chunk complete");
//                    report(fail);
                    return;
                }
            }
        }
        catch (Exception ex) {
            report(ex);
        }
    }
}
