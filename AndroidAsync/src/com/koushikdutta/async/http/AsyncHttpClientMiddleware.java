package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.http.libcore.ResponseHeaders;
import com.koushikdutta.async.util.UntypedHashtable;

import java.util.Hashtable;

public interface AsyncHttpClientMiddleware {
    public static class GetSocketData {
        public UntypedHashtable state = new UntypedHashtable();
        public AsyncHttpRequest request;
        public ConnectCallback connectCallback;
        public Cancellable socketCancellable;
    }
    
    public static class OnSocketData extends GetSocketData {
        public AsyncSocket socket;
    }
    
    public static class OnHeadersReceivedData extends OnSocketData {
        public ResponseHeaders headers;
    }
    
    public static class OnBodyData extends OnHeadersReceivedData {
        public DataEmitter bodyEmitter;
    }

    public static class OnRequestCompleteData extends OnBodyData {
        public Exception exception;
    }

    public Cancellable getSocket(GetSocketData data);
    public void onSocket(OnSocketData data);
    public void onHeadersReceived(OnHeadersReceivedData data);
    public void onBodyDecoder(OnBodyData data);
    public void onRequestComplete(OnRequestCompleteData data);
}
