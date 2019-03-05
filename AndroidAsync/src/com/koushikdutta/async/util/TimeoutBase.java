package com.koushikdutta.async.util;

import android.os.Handler;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.future.Cancellable;

public class TimeoutBase {
    protected Handlerish handlerish;
    protected long delay;

    interface Handlerish {
        void post(Runnable r);
        Object postDelayed(Runnable r, long delay);
        void removeAllCallbacks(Object cancellable);
    }

    protected void onCallback() {

    }

    public TimeoutBase(final AsyncServer server, long delay) {
        this.delay = delay;
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

    public TimeoutBase(final Handler handler, long delay) {
        this.delay = delay;
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

    public void setDelay(long delay) {
        this.delay = delay;
    }
}
