package com.koushikdutta.async;

import java.nio.ByteBuffer;

import com.koushikdutta.async.callback.WritableCallback;

public interface DataSink {
    public void write(ByteBuffer bb);
    public void write(ByteBufferList bb);
    public void setWriteableCallback(WritableCallback handler);
    public WritableCallback getWriteableCallback();
}
