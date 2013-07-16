package com.koushikdutta.async.http;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.koushikdutta.async.AsyncSSLException;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import org.apache.http.*;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpParams;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class AsyncHttpRequest {
    public RequestLine getRequestLine() {
        return new RequestLine() {
            @Override
            public String getUri() {
                return AsyncHttpRequest.this.getUri().toString();
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
                String path = AsyncHttpRequest.this.getUri().getRawPath();
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

    public RequestLine getProxyRequestLine() {
        return new RequestLine() {
            @Override
            public String getUri() {
                return AsyncHttpRequest.this.getUri().toString();
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
                return String.format("%s %s HTTP/1.1", mMethod, AsyncHttpRequest.this.getUri());
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

    public AsyncHttpRequest setMethod(String method) {
        if (getClass() != AsyncHttpRequest.class)
            throw new UnsupportedOperationException("can't change method on a subclass of AsyncHttpRequest");
        mMethod = method;
        mRawHeaders.setStatusLine(getRequestLine().toString());
        return this;
    }

    public AsyncHttpRequest(URI uri, String method) {
        this(uri, method, null);
    }

    public AsyncHttpRequest(URI uri, String method, RawHeaders headers) {
        assert uri != null;
        mMethod = method;
        if (headers == null)
            headers = new RawHeaders();
        mRawHeaders = headers;
        mHeaders = new RequestHeaders(uri, mRawHeaders);
        mRawHeaders.setStatusLine(getRequestLine().toString());
        mHeaders.setHost(uri.getHost());
        if (mHeaders.getUserAgent() == null)
            mHeaders.setUserAgent(getDefaultUserAgent());
        mHeaders.setAcceptEncoding("gzip, deflate");
        mHeaders.setConnection("keep-alive");
        mHeaders.getHeaders().set("Accept", "*/*");
    }

    public URI getUri() {
        return mHeaders.getUri();
    }
    
    private RawHeaders mRawHeaders = new RawHeaders();
    private RequestHeaders mHeaders;

    @Deprecated
    private Handler mHandler = Looper.myLooper() == null ? null : new Handler();
    @Deprecated
    public Handler getHandler() {
        return mHandler;
    }
    @Deprecated
    public AsyncHttpRequest setHandler(Handler handler) {
        mHandler = handler;
        return this;
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
    public AsyncHttpRequest setFollowRedirect(boolean follow) {
        mFollowRedirect = follow;
        return this;
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

    public static final int DEFAULT_TIMEOUT = 30000;
    int mTimeout = DEFAULT_TIMEOUT;
    public int getTimeout() {
        return mTimeout;
    }
    
    public AsyncHttpRequest setTimeout(int timeout) {
        mTimeout = timeout;
        return this;
    }
    
    public static AsyncHttpRequest create(HttpRequest request) {
        AsyncHttpRequest ret = new AsyncHttpRequest(URI.create(request.getRequestLine().getUri()), request.getRequestLine().getMethod());
        for (Header header: request.getAllHeaders()) {
            ret.getHeaders().getHeaders().add(header.getName(), header.getValue());
        }
        return ret;
    }

    private static class HttpRequestWrapper implements HttpRequest {
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

    public HttpRequest asHttpRequest() {
        return new HttpRequestWrapper(this);
    }

    public AsyncHttpRequest setHeader(String name, String value) {
        getHeaders().getHeaders().set(name, value);
        return this;
    }

    public AsyncHttpRequest addHeader(String name, String value) {
        getHeaders().getHeaders().add(name, value);
        return this;
    }

    String proxyHost;
    int proxyPort = -1;
    public void enableProxy(String host, int port) {
        proxyHost = host;
        proxyPort = port;
    }

    public void disableProxy() {
        proxyHost = null;
        proxyPort = -1;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setLogging(String tag, int level) {
        LOGTAG = tag;
        logLevel = level;
    }
    // request level logging
    String LOGTAG;
    int logLevel;
    long executionTime;
    private String getLogMessage(String message) {
        long elapsed;
        if (executionTime != 0)
            elapsed = System.currentTimeMillis() - executionTime;
        else
            elapsed = 0;
        return String.format("(%d ms) %s: %s", elapsed, getUri(), message);
    }
    public void logi(String message) {
        if (LOGTAG == null)
            return;
        if (logLevel > Log.INFO)
            return;
        Log.i(LOGTAG, getLogMessage(message));
    }
    public void logv(String message) {
        if (LOGTAG == null)
            return;
        if (logLevel > Log.VERBOSE)
            return;
        Log.v(LOGTAG, getLogMessage(message));
    }
    public void logw(String message) {
        if (LOGTAG == null)
            return;
        if (logLevel > Log.WARN)
            return;
        Log.w(LOGTAG, getLogMessage(message));
    }
    public void logd(String message) {
        if (LOGTAG == null)
            return;
        if (logLevel > Log.DEBUG)
            return;
        Log.d(LOGTAG, getLogMessage(message));
    }
    public void logd(String message, Exception e) {
        if (LOGTAG == null)
            return;
        if (logLevel > Log.DEBUG)
            return;
        Log.d(LOGTAG, getLogMessage(message));
        Log.d(LOGTAG, e.getMessage(), e);
    }
    public void loge(String message) {
        if (LOGTAG == null)
            return;
        if (logLevel > Log.ERROR)
            return;
        Log.e(LOGTAG, getLogMessage(message));
    }
    public void loge(String message, Exception e) {
        if (LOGTAG == null)
            return;
        if (logLevel > Log.ERROR)
            return;
        Log.e(LOGTAG, getLogMessage(message));
        Log.e(LOGTAG, e.getMessage(), e);
    }
}
