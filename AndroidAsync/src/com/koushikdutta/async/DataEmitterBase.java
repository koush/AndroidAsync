package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

/**
 * Created by koush on 5/27/13.
 */
public abstract class DataEmitterBase implements DataEmitter {
    boolean ended;
    protected void report(Exception e) {
        if (ended)
            return;
        ended = true;
        if (getEndCallback() != null)
            getEndCallback().onCompleted(e);
    }

    @Override
    public final void setEndCallback(CompletedCallback callback) {
        endCallback = callback;
    }

    CompletedCallback endCallback;
    @Override
    public final  CompletedCallback getEndCallback() {
        return endCallback;
    }
}
