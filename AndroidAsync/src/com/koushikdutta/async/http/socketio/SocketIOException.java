package com.koushikdutta.async.http.socketio;

/**
 * Created by koush on 8/1/13.
 */
@SuppressWarnings("serial")
public class SocketIOException extends Exception {
    public SocketIOException(String error) {
        super(error);
    }
}
