package com.koushikdutta.async.util;

import android.os.Handler;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.ValueCallback;
import com.koushikdutta.async.future.Cancellable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by koush on 7/19/16.
 */
public class ThrottleTimeout<T> {
    Handlerish handlerish;
    ValueCallback<List<T>> callback;
    long delay;
    ArrayList<T> values = new ArrayList<>();
    ThrottleMode throttleMode = ThrottleMode.Collect;

    public enum ThrottleMode {
        /**
         * The timeout will keep resetting until it expires, at which point all
         * the collected values will be invoked on the callback.
         */
        Collect,
        /**
         * The callback will be invoked immediately with the first, but future values will be
         * metered until it expires.
         */
        Meter,
    }

    private interface Handlerish {
        void post(Runnable r);
        Object postDelayed(Runnable r, long delay);
        void removeAllCallbacks(Object cancellable);
    }

    public ThrottleTimeout(final AsyncServer server, long delay, ValueCallback<List<T>> callback) {
        this.delay = delay;
        this.callback = callback;
        this.handlerish = new Handlerish() {
            @Override
            public void post(Runnable r) {
                server.post(r);
            }

            @Override
            public Object postDelayed(Runnable r, long delay) {
                return server.postDelayed(r, delay);
            }

            @Override
            public void removeAllCallbacks(Object cancellable) {
                if (cancellable == null)
                    return;
                ((Cancellable)cancellable).cancel();
            }
        };
    }

    public ThrottleTimeout(final Handler handler, long delay, ValueCallback<List<T>> callback) {
        this.delay = delay;
        this.callback = callback;
        this.handlerish = new Handlerish() {
            @Override
            public void post(Runnable r) {
                handler.post(r);
            }

            @Override
            public Object postDelayed(Runnable r, long delay) {
                handler.postDelayed(r, delay);
                return r;
            }

            @Override
            public void removeAllCallbacks(Object cancellable) {
                if (cancellable == null)
                    return;
                handler.removeCallbacks((Runnable)cancellable);
            }
        };
    }

    private void runCallback() {
        cancellable = null;
        ArrayList<T> v = new ArrayList<>(values);
        values.clear();
        callback.onResult(v);
    }

    Object cancellable;
    public synchronized void postThrottled(final T value) {
        handlerish.post(() -> {
            values.add(value);

            if (throttleMode == ThrottleMode.Collect) {
                // cancel the existing, schedule a new one, and wait.
                handlerish.removeAllCallbacks(cancellable);
                cancellable = handlerish.postDelayed(this::runCallback, delay);
            }
            else {
                // nothing is pending, so this can be fired off immediately
                if (cancellable == null) {
                    runCallback();
                }
                else {
                    handlerish.removeAllCallbacks(cancellable);
                }

                // meter future invocations
                cancellable = handlerish.postDelayed(this::runCallback, delay);
            }
        });
    }

    public void setThrottleMode(ThrottleMode throttleMode) {
        this.throttleMode = throttleMode;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }
}
