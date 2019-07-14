package com.koushikdutta.async.future;

import java.util.ArrayList;

/**
 * Created by koush on 2/25/14.
 */
public class MultiFuture<T> extends SimpleFuture<T> {
    private ArrayList<FutureCallbackInternal<T>> internalCallbacks;

    public MultiFuture() {
    }

    public MultiFuture(T value) {
        super(value);
    }

    public MultiFuture(Exception e) {
        super(e);
    }

    public MultiFuture(Future<T> future) {
        super(future);
    }

    private final FutureCallbackInternal<T> internalCallback = (e, result, callsite) -> {
        ArrayList<FutureCallbackInternal<T>> callbacks;
        synchronized (MultiFuture.this) {
            callbacks = MultiFuture.this.internalCallbacks;
            MultiFuture.this.internalCallbacks = null;
        }

        if (callbacks == null)
            return;
        for (FutureCallbackInternal<T> cb : callbacks) {
            cb.onCompleted(e, result, callsite);
        }
    };

    @Override
    protected void setCallbackInternal(FutureCallsite callsite, FutureCallbackInternal<T> internalCallback) {
        synchronized (this) {
            if (internalCallback != null) {
                if (internalCallbacks == null)
                    internalCallbacks = new ArrayList<>();
                internalCallbacks.add(internalCallback);
            }
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

        super.setCallbackInternal(callsite, this.internalCallback);
    }
}
