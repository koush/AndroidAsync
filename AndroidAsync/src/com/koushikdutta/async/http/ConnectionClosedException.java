package com.koushikdutta.async.http;

public class ConnectionClosedException extends Exception {
    public ConnectionClosedException(String message) {
        super(message);
    }
}
