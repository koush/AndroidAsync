package com.koushikdutta.async.http.socketio;

import android.os.Handler;
import android.text.TextUtils;

import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.DependentCancellable;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;

/**
 * Created by koush on 7/1/13.
 */
class SocketIOConnection {
    Handler handler;
    AsyncHttpClient httpClient;
    int heartbeat;
    ArrayList<SocketIOClient> clients = new ArrayList<SocketIOClient>();
    WebSocket webSocket;
    SocketIORequest request;

    public SocketIOConnection(Handler handler, AsyncHttpClient httpClient, SocketIORequest request) {
        this.handler = handler;
        this.httpClient = httpClient;
        this.request = request;
    }

    public boolean isConnected() {
        return webSocket != null && webSocket.isOpen();
    }

    Hashtable<String, Acknowledge> acknowledges = new Hashtable<String, Acknowledge>();
    int ackCount;
    public void emitRaw(int type, SocketIOClient client, String message, Acknowledge acknowledge) {
        String ack = "";
        if (acknowledge != null) {
            String id = "" + ackCount++;
            ack =  id + "+";
            acknowledges.put(id, acknowledge);
        }
        webSocket.send(String.format("%d:%s:%s:%s", type, ack, client.endpoint, message));
    }

    public void connect(SocketIOClient client) {
        clients.add(client);
        webSocket.send(String.format("1::%s", client.endpoint));
    }

    public void disconnect(SocketIOClient client) {
        clients.remove(client);

        // see if we can leave this endpoint completely
        boolean needsEndpointDisconnect = true;
        for (SocketIOClient other: clients) {
            // if this is the default endpoint (which disconnects everything),
            // or another client is using this endpoint,
            // we can't disconnect
            if (TextUtils.equals(other.endpoint, client.endpoint) || TextUtils.isEmpty(client.endpoint)) {
                needsEndpointDisconnect = false;
                break;
            }
        }

        if (needsEndpointDisconnect)
            webSocket.send(String.format("0::%s", client.endpoint));

        // and see if we can disconnect the socket completely
        if (clients.size() > 0)
            return;

        webSocket.setStringCallback(null);
        webSocket.setClosedCallback(null);
        webSocket.close();
        webSocket = null;
    }

    Cancellable connecting;
    void reconnect(final DependentCancellable child) {
        if (isConnected()) {
            return;
        }

        // if a connection is in progress, just wait.
        if (connecting != null && !connecting.isDone() && !connecting.isCancelled()) {
            if (child != null)
                child.setParent(connecting);
            return;
        }

        request.logi("Reconnecting socket.io");

        // dont invoke onto main handler, as it is unnecessary until a session is ready or failed
        request.setHandler(null);

        Cancellable connecting = httpClient.executeString(request)
        .then(new TransformFuture<WebSocket, String>() {
            @Override
            protected void transform(String result) throws Exception {
                String[] parts = result.split(":");
                String session = parts[0];
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

                httpClient.websocket(sessionUrl, null, null)
                .setCallback(getCompletionCallback());
            }
        })
        .setCallback(new FutureCallback<WebSocket>() {
            @Override
            public void onCompleted(Exception e, WebSocket result) {
                if (e != null) {
                    reportDisconnect(e);
                    return;
                }

                reconnectDelay = 1000L;
                SocketIOConnection.this.webSocket = result;
                attach();
            }
        });

        if (child != null)
            child.setParent(connecting);
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

    private void delayReconnect() {
        if (webSocket != null || clients.size() == 0)
            return;

        // see if any client has disconnected,
        // and that we need a reconnect
        boolean disconnected = false;
        for (SocketIOClient client: clients) {
            if (client.disconnected) {
                disconnected = true;
                break;
            }
        }

        if (!disconnected)
            return;

        httpClient.getServer().postDelayed(new Runnable() {
            @Override
            public void run() {
                reconnect(null);
            }
        }, reconnectDelay);
        reconnectDelay *= 2;
    }

    long reconnectDelay = 1000L;
    private void reportDisconnect(final Exception ex) {
        if (ex != null) {
            request.loge("socket.io disconnected", ex);
        }
        else {
            request.logi("socket.io disconnected");
        }
        select(null, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                if (client.connected) {
                    client.disconnected = true;
                    DisconnectCallback closed = client.getDisconnectCallback();
                    if (closed != null)
                        closed.onDisconnect(ex);
                }
                else {
                    // client has never connected, this is a initial connect failure
                    ConnectCallback callback = client.connectCallback;
                    if (callback != null)
                        callback.onConnectCompleted(ex, client);
                }
            }
        });

        delayReconnect();
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
                    // double connect?
//                    assert false;
                }
            }
        });
    }

    private void reportJson(String endpoint, final JSONObject jsonMessage, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                JSONCallback callback = client.jsonCallback;
                if (callback != null)
                    callback.onJSON(jsonMessage, acknowledge);
            }
        });
    }

    private void reportString(String endpoint, final String string, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                StringCallback callback = client.stringCallback;
                if (callback != null)
                    callback.onString(string, acknowledge);
            }
        });
    }

    private void reportEvent(String endpoint, final String event, final JSONArray arguments, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                client.onEvent(event, arguments, acknowledge);
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

    private Acknowledge acknowledge(final String _messageId, final String endpoint) {
        if (TextUtils.isEmpty(_messageId))
            return null;

        final String messageId = _messageId.replaceAll("\\+$", "");

        return new Acknowledge() {
            @Override
            public void acknowledge(JSONArray arguments) {
                String data = "";
                if (arguments != null)
                    data += "+" + arguments.toString();
                WebSocket webSocket = SocketIOConnection.this.webSocket;
                if (webSocket == null) {
                    final Exception e = new SocketIOException("websocket is not connected");
                    select(endpoint, new SelectCallback() {
                        @Override
                        public void onSelect(SocketIOClient client) {
                            ExceptionCallback callback = client.exceptionCallback;
                            if (callback != null)
                                callback.onException(e);
                        }
                    });
                    return;
                }
                webSocket.send(String.format("6:::%s%s", messageId, data));
            }
        };
    }

    private void attach() {
        setupHeartbeat();

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
                            reportConnect(parts[2]);
                            break;
                        case 2:
                            // heartbeat
                            webSocket.send("2::");
                            break;
                        case 3: {
                            // message
                            reportString(parts[2], parts[3], acknowledge(parts[1], parts[2]));
                            break;
                        }
                        case 4: {
                            //json message
                            final String dataString = parts[3];
                            final JSONObject jsonMessage = new JSONObject(dataString);
                            reportJson(parts[2], jsonMessage, acknowledge(parts[1], parts[2]));
                            break;
                        }
                        case 5: {
                            final String dataString = parts[3];
                            final JSONObject data = new JSONObject(dataString);
                            final String event = data.getString("name");
                            final JSONArray args = data.optJSONArray("args");
                            reportEvent(parts[2], event, args, acknowledge(parts[1], parts[2]));
                            break;
                        }
                        case 6:
                            // ACK
                            final String[] ackParts = parts[3].split("\\+", 2);
                            Acknowledge ack = acknowledges.remove(ackParts[0]);
                            if (ack == null)
                                return;
                            JSONArray arguments = null;
                            if (ackParts.length == 2)
                                arguments = new JSONArray(ackParts[1]);
                            ack.acknowledge(arguments);
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
                    webSocket.setClosedCallback(null);
                    webSocket.close();
                    webSocket = null;
                    reportDisconnect(ex);
                }
            }
        });

        // now reconnect all the sockets that may have been previously connected
        select(null, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                if (TextUtils.isEmpty(client.endpoint))
                    return;

                connect(client);
            }
        });
    }
}
