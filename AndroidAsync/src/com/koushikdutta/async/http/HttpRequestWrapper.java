package com.koushikdutta.async.http;

import org.apache.http.*;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpParams;

import java.util.List;
import java.util.Map;
/**
 * Created by koush on 5/21/13.
 */
public class HttpRequestWrapper implements HttpRequest {
    AsyncHttpRequest request;

    @Override
    public RequestLine getRequestLine() {
        return request.getRequestLine();
    }

    public HttpRequestWrapper(AsyncHttpRequest request) {
        this.request = request;
    }


    @Override
    public void addHeader(Header header) {
        request.getHeaders().getHeaders().add(header.getName(), header.getValue());
    }

    @Override
    public void addHeader(String name, String value) {
        request.getHeaders().getHeaders().add(name, value);
    }

    @Override
    public boolean containsHeader(String name) {
        return request.getHeaders().getHeaders().get(name) != null;
    }

    @Override
    public Header[] getAllHeaders() {
        Header[] ret = new Header[request.getHeaders().getHeaders().length()];
        for (int i = 0; i < ret.length; i++) {
            String name = request.getHeaders().getHeaders().getFieldName(i);
            String value = request.getHeaders().getHeaders().getValue(i);
            ret[i] = new BasicHeader(name, value);
        }
        return ret;
    }

    @Override
    public Header getFirstHeader(String name) {
        String value = request.getHeaders().getHeaders().get(name);
        if (value == null)
            return null;
        return new BasicHeader(name, value);
    }

    @Override
    public Header[] getHeaders(String name) {
        Map<String, List<String>> map = request.getHeaders().getHeaders().toMultimap();
        List<String> vals = map.get(name);
        if (vals == null)
            return new Header[0];
        Header[] ret = new Header[vals.size()];
        for (int i = 0; i < ret.length; i++)
            ret[i] = new BasicHeader(name, vals.get(i));
        return ret;
    }

    @Override
    public Header getLastHeader(String name) {
        Header[] vals = getHeaders(name);
        if (vals.length == 0)
            return null;
        return vals[vals.length - 1];
    }

    HttpParams params;
    @Override
    public HttpParams getParams() {
        return params;
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return new ProtocolVersion("HTTP", 1, 1);
    }

    @Override
    public HeaderIterator headerIterator() {
        assert false;
        return null;
    }

    @Override
    public HeaderIterator headerIterator(String name) {
        assert false;
        return null;
    }

    @Override
    public void removeHeader(Header header) {
        request.getHeaders().getHeaders().removeAll(header.getName());
    }

    @Override
    public void removeHeaders(String name) {
        request.getHeaders().getHeaders().removeAll(name);
    }

    @Override
    public void setHeader(Header header) {
        setHeader(header.getName(), header.getValue());
    }

    @Override
    public void setHeader(String name, String value) {
        request.getHeaders().getHeaders().set(name, value);
    }

    @Override
    public void setHeaders(Header[] headers) {
        for (Header header: headers)
            setHeader(header);
    }

    @Override
    public void setParams(HttpParams params) {
        this.params = params;
    }
}
