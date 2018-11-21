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

    Object cancellable;
    public synchronized void postThrottled(final T value) {
        handlerish.post(new Runnable() {
            @Override
            public void run() {
                values.add(value);

                handlerish.removeAllCallbacks(cancellable);
                cancellable = handlerish.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<T> v = new ArrayList<T>(values);
                        values.clear();
                        callback.onResult(v);
                    }
                }, delay);
            }
        });
    }
}
