package com.koushikdutta.async.http.socketio;

import android.net.Uri;

import com.koushikdutta.async.http.AsyncHttpPost;

public class SocketIORequest extends AsyncHttpPost {
    public SocketIORequest(String uri) {
        this(uri, "");
    }

    Config config;
    public Config getConfig() {
        return config;
    }

    String endpoint;
    public String getEndpoint() {
        return endpoint;
    }

    String query;
    public String getQuery() {
        return query;
    }

    public SocketIORequest(String uri, String endpoint) {
        this(uri, endpoint, null);
    }

    public SocketIORequest(String uri, String endpoint, String query) {
        this(uri, endpoint, query, null);
    }

    public SocketIORequest(String uri, String endpoint, String query, Config config) {
        super(Uri.parse(uri + (query == null ? "" : "?" + query)).buildUpon().encodedPath("/socket.io/1/").build().toString());
        this.config = (config != null) ? config : new Config();
        this.endpoint = endpoint;
        this.query = query;
    }

    public static class Config {
        boolean randomizeReconnectDelay = false;
        public void setRandomizeReconnectDelay(boolean randomizeReconnectDelay) {
            this.randomizeReconnectDelay = randomizeReconnectDelay;
        }
        public boolean isRandomizeReconnectDelay() {
            return randomizeReconnectDelay;
        }

        long reconnectDelay = 1000L;
        public void setReconnectDelay(long reconnectDelay) {
            if (reconnectDelay < 0L) {
                throw new IllegalArgumentException("reconnectDelay must be >= 0");
            }
            this.reconnectDelay = reconnectDelay;
        }
        public long getReconnectDelay() {
            return reconnectDelay;
        }

        long reconnectDelayMax = 0L;
        public void setReconnectDelayMax(long reconnectDelayMax) {
            if (reconnectDelay < 0L) {
                throw new IllegalArgumentException("reconnectDelayMax must be >= 0");
            }
            this.reconnectDelayMax = reconnectDelayMax;
        }
        public long getReconnectDelayMax() {
            return reconnectDelayMax;
        }
    }
}
