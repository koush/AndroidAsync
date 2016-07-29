package com.koushikdutta.async.http.socketio;

import android.net.Uri;
import android.text.TextUtils;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.DependentCancellable;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.socketio.transport.SocketIOTransport;
import com.koushikdutta.async.http.socketio.transport.WebSocketTransport;
import com.koushikdutta.async.http.socketio.transport.XHRPollingTransport;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;

/**
 * Created by koush on 7/1/13.
 */
class SocketIOConnection {
    AsyncHttpClient httpClient;
    int heartbeat;
    long reconnectDelay;
    ArrayList<SocketIOClient> clients = new ArrayList<SocketIOClient>();

    SocketIOTransport transport;
    SocketIORequest request;

    public SocketIOConnection(AsyncHttpClient httpClient, SocketIORequest request) {
        this.httpClient = httpClient;
        this.request = request;
        this.reconnectDelay = this.request.config.reconnectDelay;
    }

    public boolean isConnected() {
        return transport != null && transport.isConnected();
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
        transport.send(String.format(Locale.ENGLISH, "%d:%s:%s:%s", type, ack, client.endpoint, message));
    }

    public void connect(SocketIOClient client) {
        if (!clients.contains(client))
            clients.add(client);
        transport.send(String.format(Locale.ENGLISH, "1::%s", client.endpoint));
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

        final SocketIOTransport ts = transport;

        if (needsEndpointDisconnect && ts != null)
            ts.send(String.format(Locale.ENGLISH, "0::%s", client.endpoint));

        // and see if we can disconnect the socket completely
        if (clients.size() > 0 || ts == null)
            return;

        ts.setStringCallback(null);
        ts.setClosedCallback(null);
        ts.disconnect();
        transport = null;
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

        connecting = httpClient.executeString(request, null)
        .then(new TransformFuture<SocketIOTransport, String>() {
            @Override
            protected void transform(String result) throws Exception {
                String[] parts = result.split(":");
                final String sessionId = parts[0];
                if (!"".equals(parts[1]))
                    heartbeat = Integer.parseInt(parts[1]) / 2 * 1000;
                else
                    heartbeat = 0;

                String transportsLine = parts[3];
                String[] transports = transportsLine.split(",");
                HashSet<String> set = new HashSet<String>(Arrays.asList(transports));
                final SimpleFuture<SocketIOTransport> transport = new SimpleFuture<SocketIOTransport>();

                if (set.contains("websocket")) {
                    final String sessionUrl = Uri.parse(request.getUri().toString()).buildUpon()
                            .appendPath("websocket").appendPath(sessionId)
                            .build().toString();

                    httpClient.websocket(sessionUrl, null, null)
                    .setCallback(new FutureCallback<WebSocket>() {
                        @Override
                        public void onCompleted(Exception e, WebSocket result) {
                            if (e != null) {
                                transport.setComplete(e);
                                return;
                            }
                            transport.setComplete(new WebSocketTransport(result, sessionId));
                        }
                    });
                } else if (set.contains("xhr-polling")) {
                    final String sessionUrl = Uri.parse(request.getUri().toString()).buildUpon()
                            .appendPath("xhr-polling").appendPath(sessionId)
                            .build().toString();
                    XHRPollingTransport xhrPolling = new XHRPollingTransport(httpClient, sessionUrl, sessionId);
                    transport.setComplete(xhrPolling);
                } else {
                    throw new SocketIOException("transport not supported");
                }

                setComplete(transport);
            }
        })
        .setCallback(new FutureCallback<SocketIOTransport>() {
            @Override
            public void onCompleted(Exception e, SocketIOTransport result) {
                if (e != null) {
                    reportDisconnect(e);
                    return;
                }

                reconnectDelay = request.config.reconnectDelay;
                SocketIOConnection.this.transport = result;
                attach();
            }
        });

        if (child != null)
            child.setParent(connecting);
    }

    void setupHeartbeat() {
        Runnable heartbeatRunner = new Runnable() {
            @Override
            public void run() {
                final SocketIOTransport ts = transport;

                if (heartbeat <= 0 || ts == null || !ts.isConnected())
                    return;

                ts.send("2:::");
                ts.getServer().postDelayed(this, heartbeat);
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
        if (transport != null || clients.size() == 0)
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
        }, nextReconnectDelay(reconnectDelay));

        reconnectDelay = reconnectDelay * 2;
        if (request.config.reconnectDelayMax > 0L) {
            reconnectDelay = Math.min(reconnectDelay, request.config.reconnectDelayMax);
        }
    }

    private long nextReconnectDelay(long targetDelay) {
        if (targetDelay < 2L || targetDelay > (Long.MAX_VALUE >> 1) ||
            !request.config.randomizeReconnectDelay)
        {
            return targetDelay;
        }
        return (targetDelay >> 1) + (long) (targetDelay * Math.random());
    }

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
                SocketIOTransport transport = SocketIOConnection.this.transport;
                if (transport == null) {
                    final Exception e = new SocketIOException("not connected to server");
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
                transport.send(String.format(Locale.ENGLISH, "6:::%s%s", messageId, data));
            }
        };
    }

    private void attach() {
        if (transport.heartbeats())
            setupHeartbeat();

        transport.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(final Exception ex) {
                transport = null;
                reportDisconnect(ex);
            }
        });

        transport.setStringCallback(new SocketIOTransport.StringCallback() {
            @Override
            public void onStringAvailable(String message) {
                try {
//                    Log.d(TAG, "Message: " + message);
                    String[] parts = message.split(":", 4);
                    int code = Integer.parseInt(parts[0]);
                    switch (code) {
                        case 0:
                            // disconnect
                            transport.disconnect();
                            reportDisconnect(null);
                            break;
                        case 1:
                            // connect
                            reportConnect(parts[2]);
                            break;
                        case 2:
                            // heartbeat
                            transport.send("2::");
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
                            throw new SocketIOException("unknown code");
                    }
                }
                catch (Exception ex) {
                    transport.setClosedCallback(null);
                    transport.disconnect();
                    transport = null;
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
