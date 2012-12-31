package com.koushikdutta.async.callback;

public interface ContinuationCallback {
    public void onContinue(CompletedCallback next) throws Exception;
}
