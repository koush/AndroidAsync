package com.koushikdutta.async.http;

import android.os.Bundle;

import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.IAsyncSSLSocket;

public class AsyncSSLSocketMiddleware extends SimpleMiddleware {
    AsyncHttpClient mClient;
    public AsyncSSLSocketMiddleware(AsyncHttpClient client) {
        mClient = client;
        mClient.setProtocolPort("https", 443);
    }
    
    @Override
    public AsyncSocket onSocket(Bundle state, AsyncSocket socket, AsyncHttpRequest request) {
        if (!request.getUri().getScheme().equals("https"))
            return super.onSocket(state, socket, request);
        
        // don't wrap anything that is already an ssl socket
        if (com.koushikdutta.async.Util.getWrappedSocket(socket, IAsyncSSLSocket.class) != null)
            return super.onSocket(state, socket, request);
        
        return new AsyncSSLSocket(socket, request.getUri().getHost(), mClient.getProtocolPort(request.getUri()));
    }
}
