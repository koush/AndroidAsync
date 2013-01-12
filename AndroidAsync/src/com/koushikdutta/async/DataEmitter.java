package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

public interface DataEmitter extends CompletedEmitter {
    public void setDataCallback(DataCallback callback);
    public DataCallback getDataCallback();
    public boolean isChunked();
    public void pause();
    public void resume();
    public boolean isPaused();
}
