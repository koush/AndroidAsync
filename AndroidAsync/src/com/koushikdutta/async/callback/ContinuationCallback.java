package com.koushikdutta.async.callback;

import com.koushikdutta.async.future.Continuation;

public interface ContinuationCallback {
    public void onContinue(Continuation continuation, CompletedCallback next) throws Exception;
}
