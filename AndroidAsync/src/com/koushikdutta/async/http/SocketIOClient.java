package com.koushikdutta.async.http;

import java.util.Arrays;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;

public class SocketIOClient {
    public static interface SocketIOConnectCallback {
        public void onConnectCompleted(Exception ex, SocketIOClient client);
    }

    public static interface JSONCallback {
        public void onJSON(JSONObject json);
    }
    
    public static interface StringCallback {
        public void onString(String string);
    }
    
    public static interface EventCallback {
        public void onEvent(String event, JSONArray arguments);
    }
    
    private static void reportError(FutureImpl future, Handler handler, final SocketIOConnectCallback callback, final Exception e) {
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
        webSocket.send(String.format("%d:::%s", type, message));

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

    private static class FutureImpl extends SimpleFuture<SocketIOClient> {
    }
    
    public static class SocketIORequest extends AsyncHttpPost {
        String channel;
        public String getChannel() {
            return channel;
        }
        
        public SocketIORequest(String uri) {
            super(Uri.parse(uri).buildUpon().encodedPath("/socket.io/1/").build().toString());
            channel = Uri.parse(uri).getPath();
            if (TextUtils.isEmpty(channel))
                channel = null;
        }
    }
    
    public static Future<SocketIOClient> connect(final AsyncHttpClient client, String uri, final SocketIOConnectCallback callback) {
        return connect(client, new SocketIORequest(uri), callback);
    }
    
    public static Future<SocketIOClient> connect(final AsyncHttpClient client, final SocketIORequest request, final SocketIOConnectCallback callback) {
        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        final FutureImpl ret = new FutureImpl();
        
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
                    final SocketIOClient socketio = new SocketIOClient(handler, heartbeat, sessionUrl, client);
                    socketio.reconnect(callback, ret);
                }
                catch (Exception ex) {
                    reportError(ret, handler, callback, ex);
                }
            }
        });

        ret.setParent(cancel);
        
        return ret;
    }
    
    CompletedCallback closedCallback;
    public CompletedCallback getClosedCallback() {
        return closedCallback;
    }
    public void setClosedCallback(CompletedCallback callback) {
        closedCallback = callback;
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
    
    EventCallback eventCallback;
    public EventCallback getEventCallback() {
        return eventCallback;
    }
    public void setEventCallback(EventCallback callback) {
        eventCallback = callback;
    }
    
    String sessionUrl;
    WebSocket webSocket;
    AsyncHttpClient httpClient;
    private SocketIOClient(Handler handler, int heartbeat, String sessionUrl, AsyncHttpClient httpCliet) {
        this.handler = handler;
        this.heartbeat = heartbeat;
        this.sessionUrl = sessionUrl;
        this.httpClient = httpCliet;
    }
    
    public boolean isConnected() {
        return connected && !disconnected && webSocket != null && webSocket.isOpen();
    }
    
    public void disconnect() {
        webSocket.setStringCallback(null);
        webSocket.setDataCallback(null);
        webSocket.setClosedCallback(null);
        webSocket.close();
        webSocket = null;
    }
    
    private void reconnect(final SocketIOConnectCallback callback, final FutureImpl ret) {
        if (isConnected()) {
            httpClient.getServer().post(new Runnable() {
                @Override
                public void run() {
                    ret.setComplete(new Exception("already connected"));
                }
            });
            return;
        }
        connected = false;
        disconnected = false;
        Cancellable cancel = httpClient.websocket(sessionUrl, null, new WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                if (ex != null) {
                    reportError(ret, handler, callback, ex);
                    return;
                }
                
                SocketIOClient.this.webSocket = webSocket;
                attach(callback, ret);
            }
        });
        
        ret.setParent(cancel);
    }
    
    private Future<SocketIOClient> reconnect(final SocketIOConnectCallback callback) {
        FutureImpl ret = new FutureImpl();
        reconnect(callback, ret);
        return ret;
    }

    boolean connected;
    boolean disconnected;
    int heartbeat;
    void setupHeartbeat() {
        final WebSocket ws = webSocket;
        Runnable heartbeatRunner = new Runnable() {
            @Override
            public void run() {
                if (heartbeat <= 0 || disconnected || !connected || ws != webSocket || ws == null || !ws.isOpen())
                    return;
                webSocket.send("2:::");
                webSocket.getServer().postDelayed(this, heartbeat);
            }
        };
        heartbeatRunner.run();
    }
    
    Handler handler;
    private void attach(final SocketIOConnectCallback callback, final FutureImpl future) {
        webSocket.setDataCallback(new NullDataCallback());
        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(final Exception ex) {
                final boolean wasDiconnected = disconnected;
                disconnected = true;
                webSocket = null;
                Runnable runner = new Runnable() {
                    @Override
                    public void run() {
                        if (!connected) {
                            // closed connection before open...
                            callback.onConnectCompleted(ex == null ? new Exception("connection failed") : ex, null);
                        }
                        else if (!wasDiconnected) {
                            if (closedCallback != null)
                                closedCallback.onCompleted(ex == null ? new Exception("connection failed") : ex);
                        }
                    }
                };
                
                if (handler != null) {
                    AsyncServer.post(handler, runner);
                }
                else {
                    runner.run();
                }
            }
        });
        
        webSocket.setStringCallback(new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String message) {
                try {
//                    Log.d(TAG, "Message: " + message);
                    String[] parts = message.split(":", 4);
                    int code = Integer.parseInt(parts[0]);
                    switch (code) {
                    case 0:
                        if (!connected)
                            throw new Exception("received disconnect before client connect");
                        
                        disconnected = true;

                        // disconnect
                        webSocket.close();
                        
                        if (closedCallback != null) {
                            if (handler != null) {
                                AsyncServer.post(handler, new Runnable() {
                                    @Override
                                    public void run() {
                                        closedCallback.onCompleted(null);
                                    }
                                });
                            }
                            else {
                                closedCallback.onCompleted(null);
                            }
                        }
                        break;
                    case 1:
                        // connect
                        if (connected)
                            throw new Exception("received duplicate connect event");

                        if (!future.setComplete(SocketIOClient.this))
                            throw new Exception("request canceled");
                        
                        connected = true;
                        setupHeartbeat();
                        callback.onConnectCompleted(null, SocketIOClient.this);
                        break;
                    case 2:
                        // heartbeat
                        webSocket.send("2::");
                        break;
                    case 3: {
                        if (!connected)
                            throw new Exception("received message before client connect");
                                                // message
                        final String messageId = parts[1];
                        final String dataString = parts[3];
                        
                        // ack
                        if(!"".equals(messageId)) {
                            webSocket.send(String.format("6:::%s", messageId));
                        }

                        if (stringCallback != null) {
                            if (handler != null) {
                                AsyncServer.post(handler, new Runnable() {
                                    @Override
                                    public void run() {
                                        stringCallback.onString(dataString);
                                    }
                                });
                            }
                            else {
                                stringCallback.onString(dataString);
                            }
                        }
                        break;
                    }
                    case 4: {
                        if (!connected)
                            throw new Exception("received message before client connect");

                        //json message
                        final String messageId = parts[1];
                        final String dataString = parts[3];
                        
                        final JSONObject jsonMessage = new JSONObject(dataString);

                        // ack
                        if(!"".equals(messageId)) {
                            webSocket.send(String.format("6:::%s", messageId));
                        }

                        if (jsonCallback != null) {
                            if (handler != null) {
                                AsyncServer.post(handler, new Runnable() {
                                    @Override
                                    public void run() {
                                        jsonCallback.onJSON(jsonMessage);
                                    }
                                });
                            }
                            else {
                                jsonCallback.onJSON(jsonMessage);
                            }
                        }
                        break;
                    }
                    case 5: {
                        if (!connected)
                            throw new Exception("received message before client connect");
                        
                        final String messageId = parts[1];
                        final String dataString = parts[3];
                        JSONObject data = new JSONObject(dataString);
                        final String event = data.getString("name");
                        final JSONArray args = data.optJSONArray("args");

                        // ack
                        if(!"".equals(messageId)) {
                            webSocket.send(String.format("6:::%s", messageId));
                        }

                        if (eventCallback != null) {
                            if (handler != null) {
                                AsyncServer.post(handler, new Runnable() {
                                    @Override
                                    public void run() {
                                        eventCallback.onEvent(event, args);
                                    }
                                });
                            }
                            else {
                                eventCallback.onEvent(event, args);
                            }
                        }
                        break;
                    }
                    case 6:
                        // ACK
                        break;
                    case 7:
                        // error
                        throw new Exception(message);
                    case 8:
                        // noop
                        break;
                    default:
                        throw new Exception("unknown code");
                    }
                }
                catch (Exception ex) {
                    webSocket.close();
                    if (!connected) {
                        reportError(future, handler, callback, ex);
                    }
                    else {
                        disconnected = true;
                        if (closedCallback != null) {
                            closedCallback.onCompleted(ex);
                        }
                    }
                }
            }
        });
    }
}
