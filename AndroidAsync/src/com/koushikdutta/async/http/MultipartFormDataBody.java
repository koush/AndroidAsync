package com.koushikdutta.async.http;

import junit.framework.Assert;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.LineEmitter.StringCallback;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.server.BoundaryEmitter;

public class MultipartFormDataBody extends BoundaryEmitter implements AsyncHttpRequestBody {
    LineEmitter liner;

    @Override
    protected void onBoundaryStart() {
        final RawHeaders headers = new RawHeaders();
        liner = new LineEmitter();
        liner.setLineCallback(new StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                if (!"\r".equals(s)){
                    headers.addLine(s);
                }
                else {
                    liner = null;
                    setDataCallback(null);
                    if (mCallback != null)
                        mCallback.onPart(new Part(headers));
                    if (getDataCallback() == null)
                        setDataCallback(new NullDataCallback());
                }
            }
        });
        setDataCallback(liner);
    }

    @Override
    protected void report(Exception e) {
        super.report(e);
    }

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (liner != null) {
            liner.onDataAvailable(emitter, bb);
            return;
        }
        super.onDataAvailable(emitter, bb);
    }
    
    public static final String CONTENT_TYPE = "multipart/form-data";
    public MultipartFormDataBody(String contentType, String[] values) {
        for (String value: values) {
            String[] splits = value.split("=");
            if (splits.length != 2)
                continue;
            if (!"boundary".equals(splits[0]))
                continue;
            setBoundary(splits[1]);
            return;
        }
        report(new Exception ("No boundary found for multipart/form-data"));
    }

    MultipartCallback mCallback;
    public void setMultipartCallback(MultipartCallback callback) {
        mCallback = callback;
    }
    
    public MultipartCallback getMultipartCallback() {
        return mCallback;
    }

    @Override
    public void write(AsyncHttpRequest request, AsyncHttpResponse sink) {
        Assert.fail();
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean readFullyOnRequest() {
        return false;
    }

    @Override
    public int length() {
        return -1;
    }
}
