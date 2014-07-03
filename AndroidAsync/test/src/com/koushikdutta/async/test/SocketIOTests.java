package com.koushikdutta.async.test;

import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.socketio.Acknowledge;
import com.koushikdutta.async.http.socketio.ConnectCallback;
import com.koushikdutta.async.http.socketio.DisconnectCallback;
import com.koushikdutta.async.http.socketio.EventCallback;
import com.koushikdutta.async.http.socketio.JSONCallback;
import com.koushikdutta.async.http.socketio.ReconnectCallback;
import com.koushikdutta.async.http.socketio.SocketIOClient;
import com.koushikdutta.async.http.socketio.SocketIORequest;
import com.koushikdutta.async.http.socketio.StringCallback;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class SocketIOTests extends TestCase {
    public static final long TIMEOUT = 10000L;

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        AsyncServer.getDefault().stop();
    }

    class TriggerFuture extends SimpleFuture<Boolean> {
        public void trigger(boolean val) {
            setComplete(val);
        }
    }

    public void testAcknowledge() throws Exception {
        final TriggerFuture trigger = new TriggerFuture();
        SocketIOClient client = SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), "http://koush.clockworkmod.com:8080/", null).get();

        client.emit("hello", new Acknowledge() {
            @Override
            public void acknowledge(JSONArray arguments) {
                trigger.trigger("hello".equals(arguments.optString(0)));
            }
        });

        assertTrue(trigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }


    public void testSendAcknowledge() throws Exception {
        final TriggerFuture trigger = new TriggerFuture();
        SocketIOClient client = SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), "http://koush.clockworkmod.com:8080/", null).get();

        client.setStringCallback(new StringCallback() {
            boolean isEcho = true;
            @Override
            public void onString(String string, Acknowledge acknowledge) {
                if (!isEcho) {
                    trigger.trigger("hello".equals(string));
                    return;
                }
                assertNotNull(acknowledge);
                isEcho = false;
                acknowledge.acknowledge(new JSONArray().put(string));
            }
        });

        client.emit("hello");

        assertTrue(trigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    public void testEndpoint() throws Exception {
        final TriggerFuture trigger = new TriggerFuture();
        SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), new SocketIORequest("http://koush.clockworkmod.com:8080/", "/chat"), new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient client) {
                assertNull(ex);
                client.setStringCallback(new StringCallback() {
                    @Override
                    public void onString(String string, Acknowledge acknowledge) {
                        trigger.trigger("hello".equals(string));
                    }
                });
                client.emit("hello");
            }
        });
        assertTrue(trigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    public void testEchoServer() throws Exception {
        final TriggerFuture trigger1 = new TriggerFuture();
        final TriggerFuture trigger2 = new TriggerFuture();
        final TriggerFuture trigger3 = new TriggerFuture();

        SocketIORequest req = new SocketIORequest("http://koush.clockworkmod.com:8080");
        req.setLogging("Socket.IO", Log.VERBOSE);
        SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), req, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient client) {
                assertNull(ex);
                client.setStringCallback(new StringCallback() {
                    @Override
                    public void onString(String string, Acknowledge acknowledge) {
                        trigger1.trigger("hello".equals(string));
                    }
                });
                client.on("pong", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        trigger2.trigger(arguments.length() == 3);
                    }
                });
                client.setJSONCallback(new JSONCallback() {
                    @Override
                    public void onJSON(JSONObject json, Acknowledge acknowledge) {
                        trigger3.trigger("world".equals(json.optString("hello")));
                    }
                });
                try {
                    client.emit("hello");
                    client.emit(new JSONObject("{\"hello\":\"world\"}"));
                    client.emit("ping", new JSONArray("[2,3,4]"));
                }
                catch (JSONException e) {
                }
            }
        });

        assertTrue(trigger1.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(trigger2.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(trigger3.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    public void testReconnect() throws Exception {
        final TriggerFuture disconnectTrigger = new TriggerFuture();
        final TriggerFuture reconnectTrigger = new TriggerFuture();
        final TriggerFuture endpointReconnectTrigger = new TriggerFuture();
        final TriggerFuture echoTrigger = new TriggerFuture();

        SocketIORequest req = new SocketIORequest("http://koush.clockworkmod.com:8080");
        req.setLogging("socket.io", Log.VERBOSE);
        SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), req, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, final SocketIOClient client) {
                assertNull(ex);

                client.of("/chat", new ConnectCallback() {
                    @Override
                    public void onConnectCompleted(Exception ex, final SocketIOClient client) {
                        client.setReconnectCallback(new ReconnectCallback() {
                            @Override
                            public void onReconnect() {
                                client.emit("hello");
                                endpointReconnectTrigger.trigger(true);
                            }
                        });

                        client.setStringCallback(new StringCallback() {
                            @Override
                            public void onString(String string, Acknowledge acknowledge) {
                                echoTrigger.trigger("hello".equals(string));
                            }
                        });

                        AsyncServer.getDefault().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // this will trigger a reconnect
                                client.getTransport().disconnect();
                            }
                        }, 200);
                    }
                });

                client.setDisconnectCallback(new DisconnectCallback() {
                    @Override
                    public void onDisconnect(Exception e) {
                        disconnectTrigger.trigger(true);
                    }
                });

                client.setReconnectCallback(new ReconnectCallback() {
                    @Override
                    public void onReconnect() {
                        reconnectTrigger.trigger(true);
                    }
                });
            }
        });

        assertTrue(disconnectTrigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(reconnectTrigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(endpointReconnectTrigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(echoTrigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    public void testEventAck() throws Exception {
        final TriggerFuture trigger = new TriggerFuture();
        SocketIOClient client = SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), "http://koush.clockworkmod.com:8080/", null).get();

        final JSONArray args = new JSONArray();
        args.put("echo");

        client.on("scoop", new EventCallback() {
            @Override
            public void onEvent(JSONArray argument, Acknowledge acknowledge) {
                acknowledge.acknowledge(args);

            }
        });

        client.on("ack", new EventCallback() {
            @Override
            public void onEvent(JSONArray argument, Acknowledge acknowledge) {

                trigger.trigger(args.optString(0, null).equals("echo"));
            }
        });

        client.emit("poop", args);

        assertTrue(trigger.get(TIMEOUT, TimeUnit.MILLISECONDS));
    }
}
