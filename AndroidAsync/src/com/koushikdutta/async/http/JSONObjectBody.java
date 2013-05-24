package com.koushikdutta.async.http;

import org.json.JSONObject;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;

public class JSONObjectBody implements AsyncHttpRequestBody<JSONObject> {
    public JSONObjectBody() {
    }
    
    byte[] mBodyBytes;
    JSONObject json;
    public JSONObjectBody(JSONObject json) {
        this();
        this.json = json;
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
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink) {
        Util.writeAll(sink, mBodyBytes, new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
            }
        });
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override
    public int length() {
        mBodyBytes = json.toString().getBytes();
        return mBodyBytes.length;
    }

    @Override
    public JSONObject get() {
        try {
            if (json == null && data != null) {
                json = new JSONObject(data.readString());
                data = null;
            }
        }
        catch (Exception e) {
            throw new IllegalArgumentException("invalid json");
        }
        return json;
    }
}

