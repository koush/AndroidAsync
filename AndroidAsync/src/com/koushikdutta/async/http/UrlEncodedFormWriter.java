package com.koushikdutta.async.http;

import java.net.URLEncoder;
import java.util.List;

import org.apache.http.NameValuePair;

import com.koushikdutta.async.DataSink;

public class UrlEncodedFormWriter extends AsyncHttpRequestContentWriter {
    List<NameValuePair> mParameters;
    public UrlEncodedFormWriter(List<NameValuePair> parameters) {
        mParameters = parameters;
    }
    @Override
    public void write(DataSink sink) {
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
    }
}
