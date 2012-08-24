package com.koushikdutta.async;

public interface ExceptionEmitter {
    public void setExceptionCallback(ExceptionCallback callback);
    public ExceptionCallback getExceptionCallback();
}
