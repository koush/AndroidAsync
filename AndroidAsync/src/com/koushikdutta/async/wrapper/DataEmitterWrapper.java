package com.koushikdutta.async.wrapper;

import com.koushikdutta.async.DataEmitter;

public interface DataEmitterWrapper extends DataEmitter {
    public DataEmitter getDataEmitter();
}
