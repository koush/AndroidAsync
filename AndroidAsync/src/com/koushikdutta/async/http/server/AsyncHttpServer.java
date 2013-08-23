package com.koushikdutta.async.http.server;

import android.content.Context;

import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.WebSocketImpl;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.RequestHeaders;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.SSLContext;

public class AsyncHttpServer {
    ArrayList<AsyncServerSocket> mListeners = new ArrayList<AsyncServerSocket>();
    public void stop() {
        if (mListeners != null) {
            for (AsyncServerSocket listener: mListeners) {
                listener.stop();
            }
        }
    }
    
    protected void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
    }
    
    ListenCallback mListenCallback = new ListenCallback() {
        @Override
        public void onAccepted(final AsyncSocket socket) {
            AsyncHttpServerRequestImpl req = new AsyncHttpServerRequestImpl() {
                Pair match;
                String fullPath;
                String path;
                boolean responseComplete;
                boolean requestComplete;
                AsyncHttpServerResponseImpl res;
                boolean hasContinued;
                @Override
                protected void onHeadersReceived() {
                    RawHeaders headers = getRawHeaders();

                    // should the negotiation of 100 continue be here, or in the request impl?
                    // probably here, so AsyncResponse can negotiate a 100 continue.
                    if (!hasContinued && "100-continue".equals(headers.get("Expect"))) {
                        pause();
//                        System.out.println("continuing...");
                        Util.writeAll(mSocket, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(), new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                resume();
                                if (ex != null) {
                                    report(ex);
                                    return;
                                }
                                hasContinued = true;
                                onHeadersReceived();
                            }
                        });
                        return;
                    }
//                    System.out.println(headers.toHeaderString());
                    
                    String statusLine = headers.getStatusLine();
                    String[] parts = statusLine.split(" ");
                    fullPath = parts[1];
                    path = fullPath.split("\\?")[0];
                    method = parts[0];
                    synchronized (mActions) {
                        ArrayList<Pair> pairs = mActions.get(method);
                        if (pairs != null) {
                            for (Pair p: pairs) {
                                Matcher m = p.regex.matcher(path);
                                if (m.matches()) {
                                    mMatcher = m;
                                    match = p;
                                    break;
                                }
                            }
                        }
                    }
                    res = new AsyncHttpServerResponseImpl(socket, this) {
                        @Override
                        protected void onEnd() {
                            super.onEnd();
                            mSocket.setEndCallback(null);
                            responseComplete = true;
                            // reuse the socket for a subsequent request.
                            handleOnCompleted();
                        }
                    };
                    
                    onRequest(this, res);
                    
                    if (match == null) {
                        res.responseCode(404);
                        res.end();
                        return;
                    }

                    if (!getBody().readFullyOnRequest()) {
                        match.callback.onRequest(this, res);
                    }
                    else if (requestComplete) {
                        match.callback.onRequest(this, res);
                    }
                }

                @Override
                public void onCompleted(Exception e) {
                    // if the protocol was switched off http, ignore this request/response.
                    if (res.getHeaders().getHeaders().getResponseCode() == 101)
                        return;
                    requestComplete = true;
                    super.onCompleted(e);
                    // no http pipelining, gc trashing if the socket dies
                    // while the request is being sent and is paused or something
                    mSocket.setDataCallback(new NullDataCallback() {
                        @Override
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                            super.onDataAvailable(emitter, bb);
                            mSocket.close();
                        }
                    });
                    handleOnCompleted();

                    if (getBody().readFullyOnRequest()) {
                        match.callback.onRequest(this, res);
                    }
                }
                
                private void handleOnCompleted() {
                    if (requestComplete && responseComplete) {
                        onAccepted(socket);
                    }
                }

                @Override
                public String getPath() {
                    return path;
                }

                @Override
                public Multimap getQuery() {
                    String[] parts = fullPath.split("\\?", 2);
                    if (parts.length < 2)
                        return new Multimap();
                    return Multimap.parseQuery(parts[1]);
                }
            };
            req.setSocket(socket);
            socket.resume();
        }

        @Override
        public void onCompleted(Exception error) {
            report(error);
        }

        @Override
        public void onListening(AsyncServerSocket socket) {
            mListeners.add(socket);
        }
    };

    public void listen(AsyncServer server, int port) {
        server.listen(null, port, mListenCallback);
    }

    private void report(Exception ex) {
        if (mCompletedCallback != null)
            mCompletedCallback.onCompleted(ex);
    }
    
    public void listen(int port) {
        listen(AsyncServer.getDefault(), port);
    }

    public void listenSecure(final int port, final SSLContext sslContext) {
        AsyncServer.getDefault().listen(null, port, new ListenCallback() {
            @Override
            public void onAccepted(AsyncSocket socket) {
                AsyncSSLSocketWrapper sslSocket = new AsyncSSLSocketWrapper(socket, null, port, sslContext, null, null, false);
                mListenCallback.onAccepted(sslSocket);
            }

            @Override
            public void onListening(AsyncServerSocket socket) {
                mListenCallback.onListening(socket);
            }

            @Override
            public void onCompleted(Exception ex) {
                mListenCallback.onCompleted(ex);
            }
        });
    }
    
    public ListenCallback getListenCallback() {
        return mListenCallback;
    }

    CompletedCallback mCompletedCallback;
    public void setErrorCallback(CompletedCallback callback) {
        mCompletedCallback = callback;        
    }

    public CompletedCallback getErrorCallback() {
        return mCompletedCallback;
    }
    
    private static class Pair {
        Pattern regex;
        HttpServerRequestCallback callback;
    }
    
    Hashtable<String, ArrayList<Pair>> mActions = new Hashtable<String, ArrayList<Pair>>();
    
    public void addAction(String action, String regex, HttpServerRequestCallback callback) {
        Pair p = new Pair();
        p.regex = Pattern.compile("^" + regex);
        p.callback = callback;
        
        synchronized (mActions) {
            ArrayList<Pair> pairs = mActions.get(action);
            if (pairs == null) {
                pairs = new ArrayList<AsyncHttpServer.Pair>();
                mActions.put(action, pairs);
            }
            pairs.add(p);
        }
    }

    public static interface WebSocketRequestCallback {
        public void onConnected(WebSocket webSocket, RequestHeaders headers);
    }

    public void websocket(String regex, final WebSocketRequestCallback callback) {
        get(regex, new HttpServerRequestCallback() {
            @Override
            public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                boolean hasUpgrade = false;
                String connection = request.getHeaders().getHeaders().get("Connection");
                if (connection != null) {
                    String[] connections = connection.split(",");
                    for (String c: connections) {
                        if ("Upgrade".equalsIgnoreCase(c.trim())) {
                            hasUpgrade = true;
                            break;
                        }
                    }
                }
                if (!"websocket".equalsIgnoreCase(request.getHeaders().getHeaders().get("Upgrade")) || !hasUpgrade) {
                    response.responseCode(404);
                    response.end();
                    return;
                }
                callback.onConnected(new WebSocketImpl(request, response), request.getHeaders());
            }
        });
    }
    
    public void get(String regex, HttpServerRequestCallback callback) {
        addAction(AsyncHttpGet.METHOD, regex, callback);
    }
    
    public void post(String regex, HttpServerRequestCallback callback) {
        addAction(AsyncHttpPost.METHOD, regex, callback);
    }
    
    public static InputStream getAssetStream(final Context context, String asset) {
        String apkPath = context.getPackageResourcePath();
        String assetPath = "assets/" + asset;
        try {
            ZipFile zip = new ZipFile(apkPath);
            Enumeration<?> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (entry.getName().equals(assetPath)) {
                    return zip.getInputStream(entry);
                }
            }
        }
        catch (Exception ex) {
        }
        return null;
    }
    
    static Hashtable<String, String> mContentTypes = new Hashtable<String, String>();
    {
        mContentTypes.put("js", "application/javascript");
        mContentTypes.put("json", "application/json");
        mContentTypes.put("png", "image/png");
        mContentTypes.put("jpg", "image/jpeg");
        mContentTypes.put("html", "text/html");
        mContentTypes.put("css", "text/css");
        mContentTypes.put("mp4", "video/mp4");
    }
    
    public static String getContentType(String path) {
        int index = path.lastIndexOf(".");
        if (index != -1) {
            String e = path.substring(index + 1);
            String ct = mContentTypes.get(e);
            if (ct != null)
                return ct;
        }
        return "text/plain";
    }

    public void directory(Context _context, String regex, final String assetPath) {
        final Context context = _context.getApplicationContext();
        addAction("GET", regex, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String path = request.getMatcher().replaceAll("");
                InputStream is = getAssetStream(context, assetPath + path);
                if (is == null) {
                    response.responseCode(404);
                    response.end();
                    return;
                }
                response.responseCode(200);
                response.getHeaders().getHeaders().add("Content-Type", getContentType(path));
                Util.pump(is, response, new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        response.end();
                    }
                });
            }
        });
    }

    public void directory(String regex, final File directory) {
        directory(regex, directory, false);
    }
    
    public void directory(String regex, final File directory, final boolean list) {
        assert directory.isDirectory();
        addAction("GET", regex, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String path = request.getMatcher().replaceAll("");
                File file = new File(directory, path);
                
                if (file.isDirectory() && list) {
                    ArrayList<File> dirs = new ArrayList<File>();
                    ArrayList<File> files = new ArrayList<File>();
                    for (File f: file.listFiles()) {
                        if (f.isDirectory())
                            dirs.add(f);
                        else
                            files.add(f);
                    }
                    
                    Comparator<File> c = new Comparator<File>() {
                        @Override
                        public int compare(File lhs, File rhs) {
                            return lhs.getName().compareTo(rhs.getName());
                        }
                    };
                    
                    Collections.sort(dirs, c);
                    Collections.sort(files, c);
                    
                    files.addAll(0, dirs);
                    
                    return;
                }
                if (!file.isFile()) {
                    response.responseCode(404);
                    response.end();
                    return;
                }
                try {
                    FileInputStream is = new FileInputStream(file);
                    response.responseCode(200);
                    Util.pump(is, response, new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception ex) {
                            response.end();
                        }
                    });
                }
                catch (Exception ex) {
                    response.responseCode(404);
                    response.end();
                    return;
                }
            }
        });
    }
    
    private static Hashtable<Integer, String> mCodes = new Hashtable<Integer, String>();
    static {
        mCodes.put(200, "OK");
        mCodes.put(206, "Partial Content");
        mCodes.put(101, "Switching Protocols");
        mCodes.put(404, "Not Found");
    }
    
    public static String getResponseCodeDescription(int code) {
        String d = mCodes.get(code);
        if (d == null)
            return "Unknown";
        return d;
    }
}
