package com.koushikdutta.async.http;

import com.koushikdutta.async.callback.DataCallback;
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

    @Override
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        final ByteBufferList data = new ByteBufferList();
        emitter.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    completed.onCompleted(ex);
                    return;
                }
                try {
                    json = new JSONObject(data.readString());
                    completed.onCompleted(null);
                }
                catch (Exception e) {
                    completed.onCompleted(e);
                }
            }
        });
        emitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                data.add(bb);
                bb.clear();
            }
        });
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

    public static final String CONTENT_TYPE = "application/json";

    @Override
    public JSONObject get() {
        return json;
    }
}

