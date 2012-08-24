package com.koushikdutta.async.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.ExceptionCallback;
import com.koushikdutta.async.callback.DataCallback;

public class OutputStreamDataCallback implements DataCallback, ExceptionCallback {
    private OutputStream mOutput;
    public OutputStreamDataCallback(OutputStream os) {
        mOutput = os;
    }

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        try {
            for (ByteBuffer b: bb) {
                mOutput.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
            }
        }
        catch (Exception ex) {
            onException(ex);
        }
        bb.clear();
    }
    
    public void close() {
        try {
            mOutput.close();
        }
        catch (IOException e) {
            onException(e);
        }
    }

    @Override
    public void onException(Exception error) {
        error.printStackTrace();       
    }
}
