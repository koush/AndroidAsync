package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.parser.StringParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONTypeBody implements AsyncHttpRequestBody<Object> {
    public JSONTypeBody() {
    }

    byte[] mBodyBytes;
    JSONObject jsonObject;
    JSONArray jsonArray;

    public JSONTypeBody(JSONObject json) {
        this();
        jsonObject = json;
        mBodyBytes = json.toString().getBytes();
    }

    public JSONTypeBody(JSONArray jsonArray) {
        this();
        jsonArray = jsonArray;
        mBodyBytes = jsonArray.toString().getBytes();
    }

    @Override
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        new StringParser().parse(emitter).setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String result) {
                if (e != null) {
                    completed.onCompleted(e);
                    return;
                }
                try {
                    if (result.startsWith("[")) {
                        jsonArray = new JSONArray(result);
                    } else {
                        jsonObject = new JSONObject(result);
                    }
                    mBodyBytes = result.getBytes();
                    completed.onCompleted(null);
                } catch (JSONException e1) {
                    completed.onCompleted(e1);
                }
            }
        });
    }

    @Override
    public void write(AsyncHttpRequest request, DataSink sink, final CompletedCallback completed) {
        Util.writeAll(sink, mBodyBytes, completed);
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override
    public int length() {
        return mBodyBytes.length;
    }

    public static final String CONTENT_TYPE = "application/json";

    @Override
    public Object get() {
        if (jsonObject != null) {
            return jsonObject;
        } else {
            return jsonArray;
        }
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public JSONArray getJsonArray() {
        return jsonArray;
    }
}

