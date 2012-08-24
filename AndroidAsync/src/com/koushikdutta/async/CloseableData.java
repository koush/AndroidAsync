package com.koushikdutta.async;

import com.koushikdutta.async.callback.ClosedCallback;

public interface CloseableData {
    public void close();
    public void setClosedCallback(ClosedCallback handler);
    public ClosedCallback getCloseHandler();
}
