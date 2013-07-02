package com.koushikdutta.async.http.socketio;

import android.os.Handler;
import android.text.TextUtils;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Created by koush on 7/1/13.
 */
class SocketIOConnection {
    Handler handler;
    String sessionUrl;
    AsyncHttpClient httpClient;
    int heartbeat;
    ArrayList<SocketIOClient> clients = new ArrayList<SocketIOClient>();
    WebSocket webSocket;

    public SocketIOConnection(Handler handler, int heartbeat, String sessionUrl, AsyncHttpClient httpClient) {
        this.handler = handler;
        this.heartbeat = heartbeat;
        this.sessionUrl = sessionUrl;
        this.httpClient = httpClient;
    }

    public boolean isConnected() {
        return webSocket != null && webSocket.isOpen();
    }

    Hashtable<String, Acknowledge> acknowledges = new Hashtable<String, Acknowledge>();
    int ackCount;
    public void emitRaw(int type, SocketIOClient client, String message, Acknowledge acknowledge) {
        String ack = "";
        if (acknowledge != null) {
            ack = "" + ackCount++;
            acknowledges.put(ack, acknowledge);
        }
        webSocket.send(String.format("%d:%s:%s:%s", type, ack, client.endpoint, message));
    }

    public void disconnect(SocketIOClient client) {
        clients.remove(client);

        if (clients.size() > 0)
            return;

        webSocket.setStringCallback(null);
        webSocket.setClosedCallback(null);
        webSocket.close();
        webSocket = null;
    }

    void reconnect() {
        if (isConnected()) {
            assert false;
            return;
        }

        Cancellable cancel = httpClient.websocket(sessionUrl, null, new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                if (ex != null) {
                    reportDisconnect(ex);
                    return;
                }

                SocketIOConnection.this.webSocket = webSocket;
                attach();
            }
        });
    }

    void setupHeartbeat() {
        final WebSocket ws = webSocket;
        Runnable heartbeatRunner = new Runnable() {
            @Override
            public void run() {
                if (heartbeat <= 0 || ws != webSocket || ws == null || !ws.isOpen())
                    return;
                webSocket.send("2:::");
                webSocket.getServer().postDelayed(this, heartbeat);
            }
        };
        heartbeatRunner.run();
    }

    private interface SelectCallback {
        void onSelect(SocketIOClient client);
    }

    private void select(String endpoint, SelectCallback callback) {
        for (SocketIOClient client: clients) {
            if (endpoint == null || TextUtils.equals(client.endpoint, endpoint)) {
                callback.onSelect(client);
            }
        }
    }

    private void reportDisconnect(final Exception ex) {
        select(null, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                client.disconnected = true;
                DisconnectCallback closed = client.getDisconnectCallback();
                if (closed != null)
                    closed.onDisconnect(ex);
            }
        });
    }

    private void reportConnect(String endpoint) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                if (client.isConnected())
                    return;
                if (!client.connected) {
                    // normal connect
                    client.connected = true;
                    ConnectCallback callback = client.connectCallback;
                    if (callback != null)
                        callback.onConnectCompleted(null, client);
                }
                else if (client.disconnected) {
                    // reconnect
                    client.disconnected = false;
                    ReconnectCallback callback = client.reconnectCallback;
                    if (callback != null)
                        callback.onReconnect();
                }
                else {
                    assert false;
                }
            }
        });
    }

    private void reportJson(String endpoint, final JSONObject jsonMessage) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                JSONCallback callback = client.jsonCallback;
                if (callback != null)
                    callback.onJSON(jsonMessage);
            }
        });
    }

    private void reportString(String endpoint, final String string) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                StringCallback callback = client.stringCallback;
                if (callback != null)
                    callback.onString(string);
            }
        });
    }

    private void reportEvent(String endpoint, final String event, final JSONArray arguments) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                client.onEvent(event, arguments);
            }
        });
    }

    private void reportError(String endpoint, final String error) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                ErrorCallback callback = client.errorCallback;
                if (callback != null)
                    callback.onError(error);
            }
        });
    }

    private void acknowledge(String messageId) {
        if (!"".equals(messageId))
            webSocket.send(String.format("6:::%s", messageId));
    }

    private void attach() {
        webSocket.setDataCallback(new NullDataCallback());
        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(final Exception ex) {
                webSocket = null;
                reportDisconnect(ex);
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
                            // disconnect
                            webSocket.close();
                            reportDisconnect(null);
                            break;
                        case 1:
                            // connect
                            setupHeartbeat();
                            reportConnect(parts[2]);
                            break;
                        case 2:
                            // heartbeat
                            webSocket.send("2::");
                            break;
                        case 3: {
                            // message
                            acknowledge(parts[1]);
                            reportString(parts[2], parts[3]);
                            break;
                        }
                        case 4: {
                            //json message
                            final String dataString = parts[3];
                            final JSONObject jsonMessage = new JSONObject(dataString);
                            acknowledge(parts[1]);
                            reportJson(parts[2], jsonMessage);
                            break;
                        }
                        case 5: {
                            final String dataString = parts[3];
                            final JSONObject data = new JSONObject(dataString);
                            final String event = data.getString("name");
                            final JSONArray args = data.optJSONArray("args");
                            acknowledge(parts[1]);
                            reportEvent(parts[2], event, args);
                            break;
                        }
                        case 6:
                            // ACK
                            final String[] ackParts = parts[3].split("\\+", 2);
                            Acknowledge ack = acknowledges.remove(ackParts[0]);
                            if (ack == null)
                                return;
                            JSONArray ackArgs = null;
                            if (ackParts.length > 1)
                                ackArgs = new JSONArray(ackParts[1]);
                            ack.acknowledge(ackArgs);
                            break;
                        case 7:
                            // error
                            reportError(parts[2], parts[3]);
                            break;
                        case 8:
                            // noop
                            break;
                        default:
                            throw new Exception("unknown code");
                    }
                }
                catch (Exception ex) {
                    webSocket.close();
                    reportDisconnect(ex);
                }
            }
        });
    }
}
