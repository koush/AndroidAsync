package com.koushikdutta.async.http;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

import org.apache.http.NameValuePair;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class UrlEncodedFormBody implements AsyncHttpRequestBody<Multimap> {
    private Multimap mParameters;
    private byte[] mBodyBytes;

    public UrlEncodedFormBody(Multimap parameters) {
        mParameters = parameters;
    }

    public UrlEncodedFormBody(List<NameValuePair> parameters) {
        mParameters = new Multimap(parameters);
    }

    private void buildData() {
        boolean first = true;
        StringBuilder b = new StringBuilder();
        try {
            for (NameValuePair pair: mParameters) {
                if (!first)
                    b.append('&');
                first = false;

                b.append(URLEncoder.encode(pair.getName(), "UTF-8"));
                b.append('=');
                b.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
            }
            mBodyBytes = b.toString().getBytes("ISO-8859-1");
        }
        catch (UnsupportedEncodingException e) {
        }
    }
    
    @Override
    public void write(AsyncHttpRequest request, final DataSink response, final CompletedCallback completed) {
        if (mBodyBytes == null)
            buildData();
        Util.writeAll(response, mBodyBytes, completed);
    }

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        final ByteBufferList data = new ByteBufferList();
        emitter.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                bb.get(data);
            }
        });
        emitter.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    completed.onCompleted(ex);
                    return;
                }
                try {
                    mParameters = Multimap.parseQuery(data.readString());
                    completed.onCompleted(null);
                }
                catch (Exception e) {
                    completed.onCompleted(e);
                }
            }
        });
    }

    public UrlEncodedFormBody() {
    }

    @Override
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override
    public int length() {
        if (mBodyBytes == null)
            buildData();
        return mBodyBytes.length;
    }

    @Override
    public Multimap get() {
        return mParameters;
    }
}
