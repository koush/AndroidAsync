package com.koushikdutta.async.util;

import android.os.Handler;

import com.koushikdutta.async.AsyncServer;

public class IdleTimeout extends TimeoutBase {
    Runnable callback;

    public IdleTimeout(AsyncServer server, long delay, Runnable callback) {
        super(server, delay);
        this.callback = callback;

    }

    public IdleTimeout(Handler handler, long delay, Runnable callback) {
        super(handler, delay);
        this.callback = callback;
    }

    Object cancellable;
    public void reset() {
        handlerish.removeAllCallbacks(cancellable);
        handlerish.postDelayed(callback, delay);
    }
}
