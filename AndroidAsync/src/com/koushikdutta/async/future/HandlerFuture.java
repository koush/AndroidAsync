package com.koushikdutta.async.future;

import android.os.Handler;
import android.os.Looper;

/**
 * Created by koush on 12/25/13.
 */
public class HandlerFuture<T> extends SimpleFuture<T> {
    Handler handler;

    public HandlerFuture() {
        Looper looper = Looper.myLooper();
        if (looper == null)
            looper = Looper.getMainLooper();
        handler = new Handler(looper);
    }

    @Override
    public void setCallback(final FutureCallback<T> callback) {
        FutureCallback<T> wrapped = new FutureCallback<T>() {
            @Override
            public void onCompleted(final Exception e, final T result) {
                if (Looper.myLooper() == handler.getLooper()) {
                    callback.onCompleted(e, result);
                    return;
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onCompleted(e, result);
                    }
                });
            }
        };
        super.setCallback(wrapped);
    }
}
