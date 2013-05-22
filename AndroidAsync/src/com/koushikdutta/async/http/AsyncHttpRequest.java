package com.koushikdutta.async.http;

import android.os.Handler;
import android.os.Looper;
import com.koushikdutta.async.AsyncSSLException;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;

import java.net.URI;

public class AsyncHttpRequest {
    public RequestLine getRequestLine() {
        return new RequestLine() {
            
            @Override
            public String getUri() {
                return getUri().toString();
            }
            
            @Override
            public ProtocolVersion getProtocolVersion() {
                return new ProtocolVersion("HTTP", 1, 1);
            }
            
            @Override
            public String getMethod() {
                return mMethod;
            }
            
            @Override
            public String toString() {
                String path = AsyncHttpRequest.this.getUri().getPath();
                if (path.length() == 0)
                    path = "/";
                String query = AsyncHttpRequest.this.getUri().getRawQuery();
                if (query != null && query.length() != 0) {
                    path += "?" + query;
                }
                return String.format("%s %s HTTP/1.1", mMethod, path);
            }
        };
    }

    protected final String getDefaultUserAgent() {
        String agent = System.getProperty("http.agent");
        return agent != null ? agent : ("Java" + System.getProperty("java.version"));
    }
    
    private String mMethod;
    public String getMethod() {
       return mMethod; 
    }

    public AsyncHttpRequest(URI uri, String method) {
        assert uri != null;
        mMethod = method;
        mHeaders = new RequestHeaders(uri, mRawHeaders);
        mRawHeaders.setStatusLine(getRequestLine().toString());
        mHeaders.setHost(uri.getHost());
        mHeaders.setUserAgent(getDefaultUserAgent());
        mHeaders.setAcceptEncoding("gzip, deflate");
        mHeaders.getHeaders().set("Connection", "keep-alive");
        mHeaders.getHeaders().set("Accept", "*/*");
    }

    public URI getUri() {
        return mHeaders.getUri();
    }
    
    private RawHeaders mRawHeaders = new RawHeaders();
    private RequestHeaders mHeaders;
    private Handler mHandler = Looper.myLooper() == null ? null : new Handler();
    
    public Handler getHandler() {
        return mHandler;
    }
    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    public RequestHeaders getHeaders() {
        return mHeaders;
    }

    public String getRequestString() {
        return mRawHeaders.toHeaderString();
    }
    
    private boolean mFollowRedirect = true;
    public boolean getFollowRedirect() {
        return mFollowRedirect;
    }
    public void setFollowRedirect(boolean follow) {
        mFollowRedirect = follow;
    }
    
    private AsyncHttpRequestBody mBody;
    public void setBody(AsyncHttpRequestBody body) {
        mBody = body;
    }
    
    public AsyncHttpRequestBody getBody() {
        return mBody;
    }
    
    public void onHandshakeException(AsyncSSLException e) {
    }

    int mTimeout = 30000;
    public int getTimeout() {
        return mTimeout;
    }
    
    public void setTimeout(int timeout) {
        mTimeout = timeout;
    }
    
    public static AsyncHttpRequest create(HttpRequest request) {
        AsyncHttpRequest ret = new AsyncHttpRequest(URI.create(request.getRequestLine().getUri()), request.getRequestLine().getMethod());
        for (Header header: request.getAllHeaders()) {
            ret.getHeaders().getHeaders().add(header.getName(), header.getValue());
        }
        return ret;
    }

    public AsyncHttpRequest setHeader(String name, String value) {
        getHeaders().getHeaders().set(name, value);
        return this;
    }

    public AsyncHttpRequest addHeader(String name, String value) {
        getHeaders().getHeaders().add(name, value);
        return this;
    }
}
