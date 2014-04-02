package com.koushikdutta.async.http.socketio.transport;

import android.net.Uri;
import android.net.Uri.Builder;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.StringBody;

public class XHRPollingTransport implements SocketIOTransport {
    private AsyncHttpClient client;
    private Builder sessionUrl;
    private StringCallback stringCallback;
    private CompletedCallback closedCallback;
    private boolean connected;

    private static final String SEPARATOR = "\ufffd";

    public XHRPollingTransport(String sessionUrl) {
        this.client = AsyncHttpClient.getDefaultInstance();
        this.sessionUrl = Uri.parse(sessionUrl).buildUpon();

        this.doLongPolling();
        this.connected = true;
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        this.closedCallback = handler;
    }

    @Override
    public void disconnect() {
        this.connected = false;
        this.close(null);
    }

    private void close(Exception ex) {
        if (this.closedCallback != null)
            this.closedCallback.onCompleted(ex);
    }

    @Override
    public AsyncServer getServer() {
        return this.client.getServer();
    }

    @Override
    public void send(String message) {
        if (message.startsWith("5")) {
            this.postMessage(message);
            return;
        }

        AsyncHttpRequest request = new AsyncHttpGet(this.computedRequestUrl());
        request.setBody(new StringBody(message));

        this.client.executeString(request, new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                if (e != null) {
                    close(e);
                    return;
                }

                sendResult(result);
            }
        });
    }

    private void postMessage(String message) {
        if (!message.startsWith("5"))
            return;

        AsyncHttpRequest request = new AsyncHttpPost(this.computedRequestUrl());
        request.setBody(new StringBody(message));
        this.client.executeString(request);
    }

    private void doLongPolling() {
        this.client.getString(this.computedRequestUrl(), new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                if (e != null) {
                    close(e);
                    return;
                }

                sendResult(result);
                doLongPolling();
            }
        });
    }

    private void sendResult(String result) {
        if (stringCallback == null)
            return;

        if (!result.contains(SEPARATOR)) {
            this.stringCallback.onStringAvailable(result);
            return;
        }

        String [] results = result.split(SEPARATOR);
        for (int i = 1; i < results.length; i = i + 2) {
            this.stringCallback.onStringAvailable(results[i+1]);
        }
    }

    /**
     * Return an url with a time-based parameter to avoid caching issues
     */
    private String computedRequestUrl() {
        String currentTime = String.valueOf(System.currentTimeMillis());
        return this.sessionUrl.appendQueryParameter("t", currentTime)
                .build().toString();
    }

    @Override
    public void setStringCallback(StringCallback callback) {
        this.stringCallback = callback;
    }

    @Override
    public boolean heartbeats() {
        return false;
    }
}
