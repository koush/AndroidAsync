package com.koushikdutta.async;


public interface AsyncSocket extends DataEmitter, DataSink, CloseableData, CompletedEmitter {
    public boolean isConnected();
    public void pause();
    public void resume();
    public boolean isPaused();
}
