package com.koushikdutta.async.http;

import java.util.Arrays;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Looper;

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

    public static interface SocketIOCallback {
        public void on(String event, JSONArray arguments);
        public void onDisconnect(int code, String reason);
        public void onJSON(JSONObject json);
        public void onMessage(String message);
        public void onError(Exception error);
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
            handler.post(new Runnable() {
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
    
    private static class FutureImpl extends SimpleFuture<SocketIOClient> {
    }
    
    public static Future<SocketIOClient> connect(final AsyncHttpClient client, String uri, final SocketIOConnectCallback callback) {
        // get the socket.io endpoint
        final String websocketUrl = uri.replaceAll("/$", "") + "/socket.io/1/";

        final Handler handler = Looper.myLooper() == null ? null : new Handler();
        
        final FutureImpl ret = new FutureImpl();
        
        AsyncHttpPost post = new AsyncHttpPost(websocketUrl);
        // dont invoke onto main handler, as it is unnecessary until a session is ready or failed
        post.setHandler(null);
        // initiate a session
        Cancellable cancel = client.execute(post, new AsyncHttpClient.StringCallback() {
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
                    
                    Cancellable cancel = client.websocket(websocketUrl + "websocket/" + session, null, new WebSocketConnectCallback() {
                        @Override
                        public void onCompleted(Exception ex, WebSocket webSocket) {
                            if (ex != null) {
                                reportError(ret, handler, callback, ex);
                                return;
                            }
                            
                            final SocketIOClient client = new SocketIOClient(webSocket, handler, heartbeat);
                            client.attach(callback, ret);
                        }
                    });
                    
                    ret.setParent(cancel);
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
    
    WebSocket webSocket;
    private SocketIOClient(WebSocket webSocket, Handler handler, int heartbeat) {
        this.webSocket = webSocket;
        this.handler = handler;
        this.heartbeat = heartbeat;
    }
    
    boolean connected;
    boolean disconnected;
    int heartbeat;
    Runnable heartbeatRunner = new Runnable() {
        @Override
        public void run() {
            if (heartbeat <= 0 || disconnected || !connected || !webSocket.isOpen())
                return;
            webSocket.send("2:::");
            webSocket.getServer().postDelayed(this, heartbeat);
        }
    };
    
    Handler handler;
    private void attach(final SocketIOConnectCallback callback, final FutureImpl future) {
        webSocket.setDataCallback(new NullDataCallback());
        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(final Exception ex) {
                Runnable runner = new Runnable() {
                    @Override
                    public void run() {
                        if (!connected) {
                            // closed connection before open...
                            callback.onConnectCompleted(ex == null ? new Exception("connection failed") : ex, null);
                        }
                        else if (!disconnected) {
                            if (closedCallback != null)
                                closedCallback.onCompleted(ex == null ? new Exception("connection failed") : ex);
                        }
                    }
                };
                
                if (handler != null) {
                    handler.post(runner);
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
                                handler.post(new Runnable() {
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
                        heartbeatRunner.run();
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
                                handler.post(new Runnable() {
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
                                handler.post(new Runnable() {
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
                        final JSONArray args = data.getJSONArray("args");

                        // ack
                        if(!"".equals(messageId)) {
                            webSocket.send(String.format("6:::%s", messageId));
                        }

                        if (eventCallback != null) {
                            if (handler != null) {
                                handler.post(new Runnable() {
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
