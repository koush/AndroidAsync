package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;

public interface DataSink {
    public void write(ByteBufferList bb);
    public void setWritableCallback(WritableCallback handler);
    public WritableCallback getWritableCallback();
    
    public boolean isOpen();
    public void end();
    public void setClosedCallback(CompletedCallback handler);
    public CompletedCallback getClosedCallback();
    public AsyncServer getServer();
}
