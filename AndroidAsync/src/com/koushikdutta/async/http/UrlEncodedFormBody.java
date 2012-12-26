package com.koushikdutta.async.http;

import java.net.URLEncoder;
import java.util.List;

import org.apache.http.NameValuePair;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;

public class UrlEncodedFormBody implements AsyncHttpRequestBody {
    List<NameValuePair> mParameters;
    public UrlEncodedFormBody(List<NameValuePair> parameters) {
        mParameters = parameters;
    }
    @Override
    public void write(AsyncHttpRequest request, final AsyncHttpResponse response) {
        boolean first = true;
        StringBuilder b = new StringBuilder();
        for (NameValuePair pair: mParameters) {
            if (!first)
                b.append('&');
            first = false;
            b.append(URLEncoder.encode(pair.getName()));
            b.append('=');
            b.append(URLEncoder.encode(pair.getValue()));
        }
        byte[] bytes = b.toString().getBytes();
        Util.writeAll(response, bytes, new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                response.end();
            }
        });
    }
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        bb.clear();
    }
    @Override
    public void onCompleted(Exception ex) {
        System.out.println("completed");
    }
    public UrlEncodedFormBody() {
    }
}
