package com.koushikdutta.async.http.server;

import org.json.JSONObject;

import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.http.libcore.ResponseHeaders;

public interface AsyncHttpServerResponse extends DataSink {
    public void end();
    public void send(String string);
    public void send(JSONObject json);
    public void responseCode(int code);
    public ResponseHeaders getHeaders();
    public void writeHead();
}
