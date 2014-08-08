package com.koushikdutta.async.future;

/**
 * Created by koush on 12/22/13.
 */
public class FutureThread<T> extends SimpleFuture<T> {
    public FutureThread(final FutureRunnable<T> runnable) {
        this(runnable, "FutureThread");
    }

    public FutureThread(final FutureRunnable<T> runnable, String name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    setComplete(runnable.run());
                }
                catch (Exception e) {
                    setComplete(e);
                }
            }
        }, name).start();
    }
}
