package com.koushikdutta.async.http;

import java.net.URI;

public class AsyncHttpPost extends AsyncHttpRequest {
    public static final String METHOD = "POST";
    
    public AsyncHttpPost(String uri, String data, String contentType) throws Exception {
        super(new URI(uri), METHOD, data.getBytes(AsyncHttpRequest.CONTENT_ENCODING_DEFAULT), contentType, null);
    }
    
    public AsyncHttpPost(String uri, String data, String contentType, String contentEncoding) throws Exception {
        super(new URI(uri), METHOD, data.getBytes(contentEncoding), contentType, contentEncoding);
    }
    
    public AsyncHttpPost(String uri, byte[] data, String contentType, String contentEncoding) throws Exception {
        super(new URI(uri), METHOD, data, contentType, contentEncoding);
    }
    
    public AsyncHttpPost(String uri, byte[] data, String contentType) throws Exception {
        super(new URI(uri), METHOD, data, contentType, null);
    }
}
