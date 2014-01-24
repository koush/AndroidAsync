package com.koushikdutta.async.http.filter;

public class ChunkedDataException extends Exception {
    public ChunkedDataException(String message) {
        super(message);
    }
}
