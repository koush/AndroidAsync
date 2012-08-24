package com.koushikdutta.async.callback;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;


public interface DataCallback {
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb);
}
