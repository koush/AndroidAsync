package com.koushikdutta.async.future;

import android.os.Handler;

/**
 * Created by koush on 12/25/13.
 */
public class HandlerFuture<T> extends TransformFuture<T, T> {
    Handler handler = new Handler();

    @Override
    protected void error(final Exception e) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                HandlerFuture.super.error(e);
            }
        });
    }

    @Override
    protected void transform(final T result) throws Exception {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isCancelled())
                    setComplete(result);
            }
        });
    }
}
