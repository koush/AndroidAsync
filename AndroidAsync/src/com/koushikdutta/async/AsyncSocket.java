package com.koushikdutta.async;


public interface AsyncSocket extends DataEmitter, DataSink, CloseableData, CompletedEmitter {
    public AsyncServer getServer();
}
