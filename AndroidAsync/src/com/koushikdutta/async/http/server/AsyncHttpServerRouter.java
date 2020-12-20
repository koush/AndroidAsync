package com.koushikdutta.async.http.server;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpHead;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.WebSocketImpl;
import com.koushikdutta.async.util.StreamUtility;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AsyncHttpServerRouter implements RouteMatcher {

    private static class RouteInfo {
        String method;
        Pattern regex;
        HttpServerRequestCallback callback;
        AsyncHttpRequestBodyProvider bodyCallback;
    }

    final ArrayList<RouteInfo> routes = new ArrayList<>();

    public void removeAction(String action, String regex) {
        for (int i = 0; i < routes.size(); i++) {
            RouteInfo p = routes.get(i);
            if (TextUtils.equals(p.method, action) && regex.equals(p.regex.toString())) {
                routes.remove(i);
                return;
            }
        }
    }

    public void addAction(String action, String regex, HttpServerRequestCallback callback, AsyncHttpRequestBodyProvider bodyCallback) {
        RouteInfo p = new RouteInfo();
        p.regex = Pattern.compile("^" + regex);
        p.callback = callback;
        p.method = action;
        p.bodyCallback = bodyCallback;

        synchronized (routes) {
            routes.add(p);
        }
    }

    public void addAction(String action, String regex, HttpServerRequestCallback callback) {
        addAction(action, regex, callback, null);
    }

    public void websocket(String regex, final AsyncHttpServer.WebSocketRequestCallback callback) {
        websocket(regex, null, callback);
    }

    static public WebSocket checkWebSocketUpgrade(final String protocol, AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
        boolean hasUpgrade = false;
        String connection = request.getHeaders().get("Connection");
        if (connection != null) {
            String[] connections = connection.split(",");
            for (String c: connections) {
                if ("Upgrade".equalsIgnoreCase(c.trim())) {
                    hasUpgrade = true;
                    break;
                }
            }
        }
        if (!"websocket".equalsIgnoreCase(request.getHeaders().get("Upgrade")) || !hasUpgrade) {
            return null;
        }
        String peerProtocol = request.getHeaders().get("Sec-WebSocket-Protocol");
        if (!TextUtils.equals(protocol, peerProtocol)) {
            return null;
        }
        return new WebSocketImpl(request, response);
    }

    public void websocket(String regex, final String protocol, final AsyncHttpServer.WebSocketRequestCallback callback) {
        get(regex, (request, response) -> {
            WebSocket webSocket = checkWebSocketUpgrade(protocol, request, response);
            if (webSocket == null) {
                response.code(404);
                response.end();
                return;
            }

            callback.onConnected(webSocket, request);
        });
    }

    public void get(String regex, HttpServerRequestCallback callback) {
        addAction(AsyncHttpGet.METHOD, regex, callback);
    }

    public void post(String regex, HttpServerRequestCallback callback) {
        addAction(AsyncHttpPost.METHOD, regex, callback);
    }

    public static class Asset {
        public Asset(int available, InputStream inputStream, String path) {
            this.available = available;
            this.inputStream = inputStream;
            this.path = path;
        }

        public int available;
        public InputStream inputStream;
        public String path;
    }

    public static Asset getAssetStream(final Context context, String asset) {
        return getAssetStream(context.getAssets(), asset);
    }

    public static Asset getAssetStream(AssetManager am, String asset) {
        try {
            InputStream is = am.open(asset);
            return new Asset(is.available(), is, asset);
        }
        catch (IOException e) {
            final String[] extensions = new String[] { "/index.htm", "/index.html", "index.htm", "index.html", ".htm", ".html" };
            for (String ext: extensions) {
                try {
                    InputStream is = am.open(asset + ext);
                    return new Asset(is.available(), is, asset + ext);
                }
                catch (IOException ex) {
                }
            }
            return null;
        }
    }

    static Hashtable<String, String> mContentTypes = new Hashtable<>();

    static
    {
        mContentTypes.put("js", "application/javascript");
        mContentTypes.put("json", "application/json");
        mContentTypes.put("png", "image/png");
        mContentTypes.put("jpg", "image/jpeg");
        mContentTypes.put("jpeg", "image/jpeg");
        mContentTypes.put("html", "text/html");
        mContentTypes.put("css", "text/css");
        mContentTypes.put("mp4", "video/mp4");
        mContentTypes.put("mov", "video/quicktime");
        mContentTypes.put("wmv", "video/x-ms-wmv");
        mContentTypes.put("txt", "text/plain");
    }

    public static String getContentType(String path) {
        int index = path.lastIndexOf(".");
        if (index != -1) {
            String e = path.substring(index + 1);
            String ct = mContentTypes.get(e);
            if (ct != null)
                return ct;
        }
        return null;
    }

    static Hashtable<String, Future<Manifest>> AppManifests = new Hashtable<>();
    static synchronized Manifest ensureManifest(Context context) {
        Future<Manifest> future = AppManifests.get(context.getPackageName());
        if (future != null)
            return future.tryGet();

        ZipFile zip = null;
        SimpleFuture<Manifest> result = new SimpleFuture<>();
        try {
            zip = new ZipFile(context.getPackageResourcePath());
            ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
            Manifest manifest = new Manifest(zip.getInputStream(entry));
            result.setComplete(manifest);
            return manifest;
        }
        catch (Exception e) {
            result.setComplete(e);
            return null;
        }
        finally {
            StreamUtility.closeQuietly(zip);
            AppManifests.put(context.getPackageName(), result);
        }
    }

    static boolean isClientCached(Context context, AsyncHttpServerRequest request, AsyncHttpServerResponse response, String assetFileName) {
        Manifest manifest = ensureManifest(context);
        if (manifest == null)
            return false;

        try {
            String digest = manifest.getEntries().get("assets/" + assetFileName).getValue("SHA-256-Digest");
            if (TextUtils.isEmpty(digest))
                return false;

            String etag = String.format("\"%s\"", digest);
            response.getHeaders().set("ETag", etag);
            String ifNoneMatch = request.getHeaders().get("If-None-Match");
            return TextUtils.equals(ifNoneMatch, etag);
        }
        catch (Exception e) {
            Log.w(AsyncHttpServerRouter.class.getSimpleName(), "Error getting ETag for apk asset", e);
            return false;
        }
    }

    public void directory(Context context, String regex, final String assetPath) {
        AssetManager am = context.getAssets();
        addAction(AsyncHttpGet.METHOD, regex, (request, response) -> {
            String path = request.getMatcher().replaceAll("");
            Asset pair = getAssetStream(am, assetPath + path);
            if (pair == null || pair.inputStream == null) {
                response.code(404);
                response.end();
                return;
            }

            if (isClientCached(context, request, response, pair.path)) {
                StreamUtility.closeQuietly(pair.inputStream);
                response.code(304);
                response.end();
                return;
            }

            response.getHeaders().set("Content-Length", String.valueOf(pair.available));
            response.getHeaders().add("Content-Type", getContentType(pair.path));

            response.code(200);
            Util.pump(pair.inputStream, pair.available, response, ex -> {
                response.end();
                StreamUtility.closeQuietly(pair.inputStream);
            });
        });
        addAction(AsyncHttpHead.METHOD, regex, (request, response) -> {
            String path = request.getMatcher().replaceAll("");
            Asset pair = getAssetStream(am, assetPath + path);
            if (pair == null || pair.inputStream == null) {
                response.code(404);
                response.end();
                return;
            }
            StreamUtility.closeQuietly(pair.inputStream);

            if (isClientCached(context, request, response, pair.path)) {
                response.code(304);
            }
            else
            {
                response.getHeaders().set("Content-Length", String.valueOf(pair.available));
                response.getHeaders().add("Content-Type", getContentType(pair.path));
                response.code(200);
            }

            response.end();
        });
    }

    public void directory(String regex, final File directory) {
        directory(regex, directory, false);
    }

    public void directory(String regex, final File directory, final boolean list) {
        addAction(AsyncHttpGet.METHOD, regex, new HttpServerRequestCallback() {
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
                    StringBuilder builder = new StringBuilder();
                    for (File f: files) {
                        String p = new File(request.getPath(), f.getName()).getAbsolutePath();
                        builder.append(String.format("<div><a href='%s'>%s</a></div>", p, f.getName()));
                    }
                    response.send(builder.toString());

                    return;
                }
                if (!file.isFile()) {
                    response.code(404);
                    response.end();
                    return;
                }
                try {
                    FileInputStream is = new FileInputStream(file);
                    response.code(200);
                    Util.pump(is, is.available(), response, new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception ex) {
                            response.end();
                        }
                    });
                }
                catch (IOException ex) {
                    response.code(404);
                    response.end();
                }
            }
        });
    }

    public static class RouteMatch {
        public final String method;
        public final String path;
        public final Matcher matcher;
        public final HttpServerRequestCallback callback;
        public final AsyncHttpRequestBodyProvider bodyCallback;

        private RouteMatch(String method, String path, Matcher matcher, HttpServerRequestCallback callback, AsyncHttpRequestBodyProvider bodyCallback) {
            this.method = method;
            this.path = path;
            this.matcher = matcher;
            this.callback = callback;
            this.bodyCallback = bodyCallback;
        }
    }

    abstract class AsyncHttpServerRequestImpl extends com.koushikdutta.async.http.server.AsyncHttpServerRequestImpl {
        Matcher matcher;
        @Override
        public Matcher getMatcher() {
            return matcher;
        }

        @Override
        public void setMatcher(Matcher matcher) {
            this.matcher = matcher;
        }
    }

    class Callback implements HttpServerRequestCallback, RouteMatcher {
        @Override
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            RouteMatch match = route(request.getMethod(), request.getPath());
            if (match == null) {
                response.code(404);
                response.end();
                return;
            }

            match.callback.onRequest(request, response);
        }

        @Override
        public RouteMatch route(String method, String path) {
            return AsyncHttpServerRouter.this.route(method, path);
        }
    }

    private Callback callback = new Callback();

    public HttpServerRequestCallback getCallback() {
        return callback;
    }

    @Override
    public RouteMatch route(String method, String path) {
        synchronized (routes) {
            for (RouteInfo p: routes) {
                // a null method is wildcard. used for nesting routers.
                if (!TextUtils.equals(method, p.method) && p.method != null)
                    continue;
                Matcher m = p.regex.matcher(path);
                if (m.matches()) {
                    if (p.callback instanceof RouteMatcher) {
                        String subPath = m.group(1);
                        return ((RouteMatcher)p.callback).route(method, subPath);
                    }
                    return new RouteMatch(method, path, m, p.callback, p.bodyCallback);
                }
            }
        }
        return null;
    }
}
