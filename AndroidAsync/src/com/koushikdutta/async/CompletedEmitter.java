package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;

public interface CompletedEmitter {
    public void setCompletedCallback(CompletedCallback callback);
    public CompletedCallback getCompletedCallback();
}
