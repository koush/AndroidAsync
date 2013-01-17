package com.koushikdutta.async;

import javax.net.ssl.SSLPeerUnverifiedException;

public class AsyncSSLException extends SSLPeerUnverifiedException {
    public AsyncSSLException() {
        super("Peer not trusted by any of the system trust managers.");
    }
    private boolean mIgnore = false;
    public void setIgnore(boolean ignore) {
        mIgnore = ignore;
    }
    
    public boolean getIgnore() {
        return mIgnore;
    }
}
