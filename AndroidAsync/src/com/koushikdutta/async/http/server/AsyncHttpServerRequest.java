package com.koushikdutta.async.http.server;

import java.util.Map;
import java.util.regex.Matcher;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.AsyncHttpRequestBody;
import com.koushikdutta.async.http.libcore.RequestHeaders;

public interface AsyncHttpServerRequest extends DataEmitter {
    public RequestHeaders getHeaders();
    public Matcher getMatcher();
    public AsyncHttpRequestBody getBody();
    public AsyncSocket getSocket();
    public String getPath();
    public Map<String, String> getQuery();
}
