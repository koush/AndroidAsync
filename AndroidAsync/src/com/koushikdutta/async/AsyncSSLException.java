package com.koushikdutta.async;

public class AsyncSSLException extends Exception {
    public AsyncSSLException(Throwable cause) {
        super("Peer not trusted by any of the system trust managers.", cause);
    }
    private boolean mIgnore = false;
    public void setIgnore(boolean ignore) {
        mIgnore = ignore;
    }
    
    public boolean getIgnore() {
        return mIgnore;
    }
}
