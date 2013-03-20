package com.koushikdutta.async.http;

import org.json.JSONObject;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;

public class JSONRequestBody implements AsyncHttpRequestBody {
    public JSONRequestBody() {
    }
    
    byte[] mBodyBytes;
    JSONObject json;
    public JSONRequestBody(JSONObject json) {
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
}
