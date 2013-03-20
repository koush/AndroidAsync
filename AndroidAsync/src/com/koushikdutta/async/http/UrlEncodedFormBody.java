package com.koushikdutta.async.http;

import java.io.UnsupportedEncodingException;
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
    private byte[] mBodyBytes;
    
    public UrlEncodedFormBody(Iterable<NameValuePair> parameters) {
        mParameters = parameters;
        buildData();
    }
    
    private void buildData() {
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
        try {
            mBodyBytes = b.toString().getBytes("ISO-8859-1");
        }
        catch (UnsupportedEncodingException e) {
        }
    }
    
    @Override
    public void write(AsyncHttpRequest request, final AsyncHttpResponse response) {
        Util.writeAll(response, mBodyBytes, new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
//                response.end();
            }
        });
    }

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    private ByteBufferList data;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (data == null)
            data = new ByteBufferList();
        data.add(bb);
        bb.clear();
    }
    
    public static Map<String, String> parse(String data) {
        HashMap<String, String> map = new HashMap<String, String>();
        String[] pairs = data.split("&");
        for (String p : pairs) {
            String[] pair = p.split("=", 2);
            if (pair.length == 0)
                continue;
            String name = Uri.decode(pair[0]);
            String value = null;
            if (pair.length == 2)
                value = Uri.decode(pair[1]);
            map.put(name, value);
        }
        return Collections.unmodifiableMap(map);
    }

    public Iterable<NameValuePair> getParameters() {
        if (mParameters == null && data != null) {
            ArrayList<NameValuePair> params;
            mParameters = params = new ArrayList<NameValuePair>();
            String[] pairs = data.peekString().split("&");
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
        return mParameters;
    }
    
    public Map<String, String> getParameterMap() {
        HashMap<String, String> map = new HashMap<String, String>();
        for (NameValuePair pair: getParameters()) {
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

    @Override
    public int length() {
        return mBodyBytes.length;
    }
}
