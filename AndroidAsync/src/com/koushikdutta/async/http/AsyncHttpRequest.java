package com.koushikdutta.async.http;

import java.io.ByteArrayOutputStream;
import java.net.URI;

import junit.framework.Assert;

import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.RequestHeaders;

public class AsyncHttpRequest {
    public String getRequestLine() {
        String path = getUri().getPath();
        if (path.length() == 0)
            path = "/";
        String query = getUri().getQuery();
        if (query != null && query.length() != 0)
            path += "?" + getUri().getQuery();
        return String.format("%s %s HTTP/1.1", mMethod, path);
    }

    protected final String getDefaultUserAgent() {
        String agent = System.getProperty("http.agent");
        return agent != null ? agent : ("Java" + System.getProperty("java.version"));
    }
    
    private String mMethod;
    public String getMethod() {
       return mMethod; 
    }
    
    private byte[] mData;
    public byte[] getData() {
        return mData;
    }
    
    protected static String CONTENT_ENCODING_DEFAULT = "UTF-8";
    private String mContentEncoding;
    public String getContentEncoding() {
        if (mContentEncoding != null) { 
            return mContentEncoding; 
        }
        return CONTENT_ENCODING_DEFAULT;
    }

    public AsyncHttpRequest(URI uri, String method, byte[] data, String contentType, String contentEncoding) {
        Assert.assertNotNull(uri);
        Assert.assertNotNull(method);
        mMethod = method;
        mData = data;
        mHeaders = new RequestHeaders(uri, mRawHeaders);
        mRawHeaders.setStatusLine(getRequestLine());
        mHeaders.setHost(uri.getHost());
        mHeaders.setUserAgent(getDefaultUserAgent());
        mHeaders.setAcceptEncoding("gzip");
        if (contentType != null){
            mHeaders.setContentType(contentType);
        }
        if (mData != null){
            mHeaders.setContentLength(mData.length);
        }
        if (contentEncoding != null){
            mContentEncoding = contentEncoding;
        }
    }
    

    public URI getUri() {
        return mHeaders.getUri();
    }
    
    private RawHeaders mRawHeaders = new RawHeaders();
    private RequestHeaders mHeaders;
    
    public RequestHeaders getHeaders() {
        return mHeaders;
    }

    public String getRequestString() {
        return mRawHeaders.toHeaderString();
    }
    
    public byte[] getRequestData(){
        ByteArrayOutputStream responseDataArray = new ByteArrayOutputStream();
        try {
            responseDataArray.write(mRawHeaders.toHeaderString().getBytes(getContentEncoding()));
            if (mData != null){
                responseDataArray.write(mData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return responseDataArray.toByteArray();
    }
    
    private boolean mFollowRedirect = true;
    public boolean getFollowRedirect() {
        return mFollowRedirect;
    }
    public void setFollowRedirect(boolean follow) {
        mFollowRedirect = follow;
    }
}
