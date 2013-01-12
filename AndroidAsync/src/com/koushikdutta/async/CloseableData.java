package com.koushikdutta.async;

import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;

public interface CloseableData {
    public boolean isOpen();
    public void close();
    public void setClosedCallback(CompletedCallback handler);
    public CompletedCallback getCloseHandler();
}
