package com.koushikdutta.async.http;

import com.koushikdutta.async.Cancelable;

public class SimpleMiddleware implements AsyncHttpClientMiddleware {

    @Override
    public Cancelable getSocket(GetSocketData data) {
        return null;
    }

    @Override
    public void onSocket(OnSocketData data) {
        
    }

    @Override
    public void onHeadersReceived(OnHeadersReceivedData data) {
        
    }

    @Override
    public void onBodyDecoder(OnBodyData data) {
        
    }

    @Override
    public void onRequestComplete(OnRequestCompleteData data) {
        
    }

}
