package com.koushikdutta.async.http.socketio;

import android.os.Handler;
import android.os.Looper;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;

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

    private void emitRaw(int type, String message) {
        connection.emitRaw(type, endpoint, message);
    }

    public void emit(String name, JSONArray args) {
        final JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            event.put("args", args);
            emitRaw(5, event.toString());
        }
        catch (Exception e) {
        }
    }

    public void emit(final String message) {
        emitRaw(3, message);
    }
    
    public void emit(final JSONObject jsonMessage) {
        emitRaw(4, jsonMessage.toString());
    }

    public static Future<SocketIOClient> connect(final AsyncHttpClient client, String uri, final ConnectCallback callback) {
        return connect(client, uri, null, callback);
    }

    public static Future<SocketIOClient> connect(final AsyncHttpClient client, String uri, String channel, final ConnectCallback callback) {
        return connect(client, new SocketIORequest(uri, channel), callback);
    }

    ConnectCallback connectCallback;
    public static Future<SocketIOClient> connect(final AsyncHttpClient client, final SocketIORequest request, final ConnectCallback callback) {
        final Handler handler = Looper.myLooper() == null ? null : request.getHandler();
        final SimpleFuture<SocketIOClient> ret = new SimpleFuture<SocketIOClient>();

        // dont invoke onto main handler, as it is unnecessary until a session is ready or failed
        request.setHandler(null);
        // initiate a session
        Cancellable cancel = client.executeString(request, new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(final Exception e, AsyncHttpResponse response, String result) {
                if (e != null) {
                    reportError(ret, handler, callback, e);
                    return;
                }
                
                try {
                    String[] parts = result.split(":");
                    String session = parts[0];
                    final int heartbeat;
                    if (!"".equals(parts[1]))
                        heartbeat = Integer.parseInt(parts[1]) / 2 * 1000;
                    else
                        heartbeat = 0;
                    
                    String transportsLine = parts[3];
                    String[] transports = transportsLine.split(",");
                    HashSet<String> set = new HashSet<String>(Arrays.asList(transports));
                    if (!set.contains("websocket"))
                        throw new Exception("websocket not supported");
                    
                    final String sessionUrl = request.getUri().toString() + "websocket/" + session + "/";
                    final SocketIOConnection connection = new SocketIOConnection(handler, heartbeat, sessionUrl, client);
                    final SocketIOClient socketio = new SocketIOClient(connection, request.getEndpoint(), callback);
                    socketio.connection.reconnect();
                }
                catch (Exception ex) {
                    reportError(ret, handler, callback, ex);
                }
            }
        });

        ret.setParent(cancel);
        
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
        connection.clients.add(this);
    }
    
    public boolean isConnected() {
        return connected && !disconnected && connection.isConnected();
    }
    
    public void disconnect() {
        connection.disconnect(this);
        if (disconnectCallback != null) {
        	disconnectCallback.onDisconnect(null);
        }
    }
}
