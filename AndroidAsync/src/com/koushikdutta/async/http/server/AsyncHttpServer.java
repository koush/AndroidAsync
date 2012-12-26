package com.koushikdutta.async.http.server;

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

import junit.framework.Assert;
import android.content.Context;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ExceptionCallback;
import com.koushikdutta.async.ExceptionEmitter;
import com.koushikdutta.async.NullDataCallback;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.libcore.RawHeaders;

public class AsyncHttpServer implements ExceptionEmitter {
    AsyncServerSocket mListener;
    public void stop() {
        if (mListener != null)
            mListener.stop();
    }
    public AsyncHttpServer(AsyncServer server, int port) {
        server.listen(null, port, new ListenCallback() {
            @Override
            public void onAccepted(final AsyncSocket socket) {
                AsyncHttpServerRequestImpl req = new AsyncHttpServerRequestImpl() {
                    @Override
                    protected void onHeadersReceived() {
                        super.onHeadersReceived();
                        
                        socket.setDataCallback(new NullDataCallback());
                        RawHeaders headers = getRawHeaders();
                        
                        String statusLine = headers.getStatusLine();
                        String[] parts = statusLine.split(" ");
                        String path = parts[1];
                        path = path.split("\\?")[0];
                        String action = parts[0];
                        Pair match = null;
                        synchronized (mActions) {
                            ArrayList<Pair> pairs = mActions.get(action);
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
                        AsyncHttpServerResponseImpl res = new AsyncHttpServerResponseImpl(socket) {
                            @Override
                            protected void onCompleted() {
                                // reuse the socket for a subsequent request.
                                onAccepted(socket);
                            }
                        };
                        if (match == null) {
                            res.responseCode(404);
                            res.end();
                            return;
                        }

                        match.callback.onRequest(this, res);
                    }
                    @Override
                    protected void report(Exception e) {
                        super.report(e);
                        AsyncHttpServer.this.report(e);
                    }
                };
                req.setSocket(socket);
            }

            @Override
            public void onException(Exception error) {
                report(error);
            }

            @Override
            public void onListening(AsyncServerSocket socket) {
                mListener = socket;
            }
        });
    }

    private void report(Exception ex) {
        if (mExceptionCallback != null)
            mExceptionCallback.onException(ex);
    }
    
    public AsyncHttpServer(int port) {
        this(AsyncServer.getDefault(), port);
    }

    ExceptionCallback mExceptionCallback;
    @Override
    public void setExceptionCallback(ExceptionCallback callback) {
        mExceptionCallback = callback;        
    }

    @Override
    public ExceptionCallback getExceptionCallback() {
        return mExceptionCallback;
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

    public void websocket(String regex, final WebSocketCallback callback) {
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
                if (!"websocket".equals(request.getHeaders().getHeaders().get("Upgrade")) || !hasUpgrade) {
                    response.responseCode(404);
                    response.end();
                    return;
                }
                AsyncHttpServerRequestImpl impl = (AsyncHttpServerRequestImpl)request;
                
                callback.onConnected(new WebSocketImpl(impl, response));
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
        Assert.assertTrue(directory.isDirectory());
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
