# AndroidAsync

### AndroidAsync is a java.nio based socket and http library for Android.
It uses java.nio to manage connections. All the connections are thus managed on a *single* thread, rather than one per thread. 
NIO is extremely efficient.


### Download a url to a String

```java
// url is the URL to download. The callback will be invoked on the UI thread
// once the download is complete.
AsyncHttpClient.getDefaultInstance().get(url, new AsyncHttpClient.StringCallback() {
    // Callback is invoked with any exceptions/errors, and the result, if available.
    @Override
    public void onCompleted(Exception e, String result) {
        if (e != null) {
            e.printStackTrace();
            return;
        }
        System.out.println("I got a string: " + result);
    }
});

```




### Download a url to a file

```java
AsyncHttpClient.getDefaultInstance().get(url, filename, new AsyncHttpClient.FileCallback() {
    @Override
    public void onCompleted(Exception e, File result) {
        if (e != null) {
            e.printStackTrace();
            return;
        }
        System.out.println("my file is available at: " + result.getAbsolutePath());
    }
});

```



### Caching is supported too (experimental)

```java
// arguments are the http client, the directory to store cache files, and the size of the cache in bytes
ResponseCacheMiddleware.addCache(AsyncHttpClient.getDefaultInstance(),
                                  getFileStreamPath("asynccache"),
                                  1024 * 1024 * 10);
```


### Can also create web sockets:

```java
AsyncHttpClient.getDefaultInstance().websocket(get, "my-protocol", new WebSocketConnectCallback() {
    @Override
    public void onCompleted(Exception ex, WebSocket webSocket) {
        if (ex != null) {
            ex.printStackTrace();
            return;
        }

        webSocket.send("a string");
        webSocket.send(new byte[10]);
        
        webSocket.setStringCallback(new StringCallback() {
            public void onStringAvailable(String s) {
                System.out.println("I got a string: " + s);
            }
        });
        
        webSocket.setDataCallback(new DataCallback() {
            public void onDataAvailable(ByteBufferList byteBufferList) {
                System.out.println("I got some bytes!");
                // note that this data has been read
                byteBufferList.clear();
            }
        });
    }
});
```


### AndroidAsync also supports socket.io

```java
SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), "http://192.168.1.2:3000", new SocketIOConnectCallback() {
    @Override
    public void onConnectCompleted(Exception ex, SocketIOClient client) {
        if (ex != null) {
            ex.printStackTrace();
            return;
        }

        client.setStringCallback(new StringCallback() {
            @Override
            public void onString(String string) {
                System.out.println(string);
            }
        });
        
        client.setEventCallback(new EventCallback() {
            @Override
            public void onEvent(String event, JSONArray arguments) {
                System.out.println("event: " + event + " args: " + arguments.toString());
            }
        });
        
        client.setJSONCallback(new JSONCallback() {
            @Override
            public void onJSON(JSONObject json) {
                System.out.println("json: " + json.toString());
            }
        });
    }
});
```


### Need to do multipart/form-data uploads? That works too.

```java
AsyncHttpPost post = new AsyncHttpPost("http://myservercom/postform.html");
MultipartFormDataBody body = new MultipartFormDataBody();
body.addFilePart("my-file", new File("/path/to/file.txt");
body.addStringPart("foo", "bar");
post.setBody(body);

AsyncHttpClient.getDefaultInstance().execute(post, new StringCallback() {
    @Override
    public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
        if (e != null) {
            ex.printStackTrace();
            return;
        }
        System.out.println("Server says: " + result);
    }
});
```


### AndroidAsync also let's you create simple HTTP servers (and websocket servers):

```java
// listen on port 5000
AsyncHttpServer mServer = new AsyncHttpServer(5000);
mServer.get("/", new HttpServerRequestCallback() {
    @Override
    public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        response.send("Hello!!!");
    }
});
// browsing http://localhost:5000 will return Hello!!!
```
