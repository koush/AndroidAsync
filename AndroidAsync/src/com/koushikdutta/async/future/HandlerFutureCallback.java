package com.koushikdutta.async.future;

import android.os.Handler;

/**
 * Created by koush on 12/22/13.
 */
public abstract class HandlerFutureCallback<T> implements FutureCallback<T> {
    Handler handler = new Handler();

    public abstract void onHandlerCompleted(final Exception e, final T result);

    @Override
    public final void onCompleted(final Exception e, final T result) {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onHandlerCompleted(e, result);
                }
            });
            return;
        }

        onHandlerCompleted(e, result);
    }
}
