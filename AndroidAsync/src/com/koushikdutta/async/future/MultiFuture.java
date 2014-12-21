package com.koushikdutta.async.future;

import java.util.ArrayList;

/**
 * Created by koush on 2/25/14.
 */
public class MultiFuture<T> extends SimpleFuture<T> {
    ArrayList<FutureCallback<T>> callbacks;

    final FutureCallback<T> callback = new FutureCallback<T>() {
        @Override
        public void onCompleted(Exception e, T result) {
            ArrayList<FutureCallback<T>> callbacks;
            synchronized (MultiFuture.this) {
                callbacks = MultiFuture.this.callbacks;
                MultiFuture.this.callbacks = null;
            }

            if (callbacks == null)
                return;
            for (FutureCallback<T> cb: callbacks) {
                cb.onCompleted(e, result);
            }
        }
    };

    @Override
    public MultiFuture<T> setCallback(FutureCallback<T> callback) {
        synchronized (this) {
            if (callbacks == null)
                callbacks = new ArrayList<FutureCallback<T>>();
            callbacks.add(callback);
        }
        // so, there is a race condition where this internal callback could get
        // executed twice, if two callbacks are added at the same time.
        // however, it doesn't matter, as the actual retrieval and nulling
        // of the callback list is done in another sync block.
        // one of the invocations will actually invoke all the callbacks,
        // while the other will not get a list back.

        // race:
        // 1-ADD
        // 2-ADD
        // 1-INVOKE LIST
        // 2-INVOKE NULL
        super.setCallback(this.callback);
        return this;
    }
}
