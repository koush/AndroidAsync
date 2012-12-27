package com.koushikdutta.async.http;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.net.Uri;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;

public class UrlEncodedFormBody implements AsyncHttpRequestBody {
    private Iterable<NameValuePair> mParameters;
    public UrlEncodedFormBody(Iterable<NameValuePair> parameters) {
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

    private ByteBufferList data = null;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (data == null)
            data = new ByteBufferList();
        data.add(bb);
        bb.clear();
    }

    @Override
    public void onCompleted(Exception ex) {
        ArrayList<NameValuePair> params;
        mParameters = params = new ArrayList<NameValuePair>();
        String[] pairs = data.getString().split("&");
        for (String p : pairs) {
            String[] pair = p.split("=", 2);
            if (pair.length == 0)
                continue;
            String name = Uri.decode(pair[0]);
            String value = null;
            if (pair.length == 2)
                value = Uri.decode(pair[1]);
            params.add(new BasicNameValuePair(name, value));
        }
    }
    
    public Iterable<NameValuePair> getParameters() {
        return mParameters;
    }
    
    public Map<String, String> getParameterMap() {
        HashMap<String, String> map = new HashMap<String, String>();
        for (NameValuePair pair: mParameters) {
            if (!map.containsKey(pair.getName()))
                map.put(pair.getName(), pair.getValue());
        }
        return Collections.unmodifiableMap(map);
    }

    public UrlEncodedFormBody() {
    }

    @Override
    public boolean readFullyOnRequest() {
        return true;
    }
}
