package com.koushikdutta.async.http.socketio;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONObject;

public class SocketIOClient extends EventEmitter {
    boolean connected;
    boolean disconnected;

    private static void reportError(SimpleFuture<SocketIOClient> future, Handler handler, final ConnectCallback callback, final Exception e) {
        if (!future.setComplete(e))
            return;
        if (handler != null) {
            AsyncServer.post(handler, new Runnable() {
                @Override
                public void run() {
                    callback.onConnectCompleted(e, null);
                }
            });
        }
        else {
            callback.onConnectCompleted(e, null);
        }
    }

    private void emitRaw(int type, String message, Acknowledge acknowledge) {
        connection.emitRaw(type, this, message, acknowledge);
    }

    public void emit(String name, JSONArray args) {
        emit(name, args, null);
    }

    public void emit(final String message) {
        emit(message, (Acknowledge)null);
    }

    public void emit(final JSONObject jsonMessage) {
        emit(jsonMessage, null);
    }

    public void emit(String name, JSONArray args, Acknowledge acknowledge) {
        final JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            event.put("args", args);
            emitRaw(5, event.toString(), acknowledge);
        }
        catch (Exception e) {
        }
    }

    public void emit(final String message, Acknowledge acknowledge) {
        emitRaw(3, message, acknowledge);
    }

    public void emit(final JSONObject jsonMessage, Acknowledge acknowledge) {
        emitRaw(4, jsonMessage.toString(), acknowledge);
    }

    public static Future<SocketIOClient> connect(final AsyncHttpClient client, String uri, final ConnectCallback callback) {
        return connect(client, new SocketIORequest(uri), callback);
    }

    ConnectCallback connectCallback;
    public static Future<SocketIOClient> connect(final AsyncHttpClient client, final SocketIORequest request, final ConnectCallback callback) {
        final Handler handler = Looper.myLooper() == null ? null : request.getHandler();
        final SimpleFuture<SocketIOClient> ret = new SimpleFuture<SocketIOClient>();

        final SocketIOConnection connection = new SocketIOConnection(handler, client, request);

        final ConnectCallback wrappedCallback = new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient client) {
                if (ex != null || TextUtils.isEmpty(request.getEndpoint())) {
                    if (callback != null)
                        callback.onConnectCompleted(ex, client);
                    ret.setComplete(ex, client);
                    return;
                }

                // remove the root client since that's not actually being used.
                connection.clients.remove(client);

                // connect to the endpoint we want
                client.of(request.getEndpoint(), new ConnectCallback() {
                    @Override
                    public void onConnectCompleted(Exception ex, SocketIOClient client) {
                        if (callback != null)
                            callback.onConnectCompleted(ex, client);
                        ret.setComplete(ex, client);
                    }
                });
            }
        };

        connection.clients.add(new SocketIOClient(connection, "", wrappedCallback));
        connection.reconnect(ret);

        return ret;
    }

    ErrorCallback errorCallback;
    public ErrorCallback getErrorCallback() {
        return errorCallback;
    }
    public void setErrorCallback(ErrorCallback callback) {
        errorCallback = callback;
    }

    DisconnectCallback disconnectCallback;
    public DisconnectCallback getDisconnectCallback() {
        return disconnectCallback;
    }
    public void setDisconnectCallback(DisconnectCallback callback) {
        disconnectCallback = callback;
    }

    ReconnectCallback reconnectCallback;
    public ReconnectCallback getReconnectCallback() {
        return reconnectCallback;
    }
    public void setReconnectCallback(ReconnectCallback callback) {
        reconnectCallback = callback;
    }

    JSONCallback jsonCallback;
    public JSONCallback getJSONCallback() {
        return jsonCallback;
    }
    public void setJSONCallback(JSONCallback callback) {
        jsonCallback = callback;
    }
    
    StringCallback stringCallback;
    public StringCallback getStringCallback() {
        return stringCallback;
    }
    public void setStringCallback(StringCallback callback) {
        stringCallback = callback;
    }

    SocketIOConnection connection;
    String endpoint;
    private SocketIOClient(SocketIOConnection connection, String endpoint, ConnectCallback callback) {
        this.endpoint = endpoint;
        this.connection = connection;
        this.connectCallback = callback;
    }
    
    public boolean isConnected() {
        return connected && !disconnected && connection.isConnected();
    }
    
    public void disconnect() {
        connection.disconnect(this);
        DisconnectCallback disconnectCallback = this.disconnectCallback;
        if (disconnectCallback != null) {
        	disconnectCallback.onDisconnect(null);
        }
    }

    public void of(String endpoint, ConnectCallback connectCallback) {
        connection.connect(new SocketIOClient(connection, endpoint, connectCallback));
    }

    public void reconnect() {
        connection.reconnect(null);
    }

    public WebSocket getWebSocket() {
        return connection.webSocket;
    }
}
