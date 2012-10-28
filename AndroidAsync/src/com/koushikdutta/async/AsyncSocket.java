package com.koushikdutta.async;


public interface AsyncSocket extends DataExchange, CloseableData, ExceptionEmitter {
    public boolean isConnected();
    public void pause();
    public void resume();
    public boolean isPaused();
}
