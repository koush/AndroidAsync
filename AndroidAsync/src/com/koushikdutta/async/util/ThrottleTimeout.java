package com.koushikdutta.async.util;

import android.os.Handler;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.ValueCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by koush on 7/19/16.
 */
public class ThrottleTimeout<T> extends TimeoutBase {
    ValueCallback<List<T>> callback;
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


    public ThrottleTimeout(final AsyncServer server, long delay, ValueCallback<List<T>> callback) {
        super(server, delay);
        this.callback = callback;
    }

    public ThrottleTimeout(final Handler handler, long delay, ValueCallback<List<T>> callback) {
        super(handler, delay);
        this.callback = callback;
    }

    public void setCallback(ValueCallback<List<T>> callback) {
        this.callback = callback;
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

                    // meter future invocations
                    cancellable = handlerish.postDelayed(this::runCallback, delay);
                }
            }
        });
    }

    public void setThrottleMode(ThrottleMode throttleMode) {
        this.throttleMode = throttleMode;
    }
}
