package com.koushikdutta.async.http;

import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;

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
//        System.out.println("completed url form body");

        try {
            mParameters = URLEncodedUtils.parse(new StringEntity(data.getString()));
        }
        catch (Exception e) {
            e.printStackTrace();
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
