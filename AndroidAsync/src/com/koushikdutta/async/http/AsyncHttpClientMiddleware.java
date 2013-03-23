package com.koushikdutta.async.http;

import android.os.Bundle;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.Cancelable;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public interface AsyncHttpClientMiddleware {
    public static class GetSocketData {
        Bundle state = new Bundle();
        AsyncHttpRequest request;
        ConnectCallback connectCallback;
    }
    
    public static class OnSocketData extends GetSocketData {
        AsyncSocket socket;
    }
    
    public static class OnHeadersReceivedData extends OnSocketData {
        ResponseHeaders headers;
    }
    
    public static class OnBodyData extends OnHeadersReceivedData {
        DataEmitter bodyEmitter;
    }
    
    public static class OnRequestCompleteData extends OnBodyData {
        Exception exception;
    }
    
    public Cancelable getSocket(GetSocketData data);
    public void onSocket(OnSocketData data);
    public void onHeadersReceived(OnHeadersReceivedData data);
    public void onBodyDecoder(OnBodyData data);
    public void onRequestComplete(OnRequestCompleteData data);
}
