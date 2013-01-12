package com.koushikdutta.async;


public interface AsyncSocket extends DataEmitter, DataSink {
    public AsyncServer getServer();
}
