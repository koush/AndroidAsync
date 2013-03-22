# AndroidAsync

### AndroidAsync is a java.nio based socket and http library for Android.
It uses java.nio to manage connections. All the connections are thus managed on a *single* thread, rather than one per thread. 
NIO is extremely efficient.


### Download a url to a String

```java
// url is the URL to download. The callback will be invoked on the UI thread
// once the download is complete.
AsyncHttpClient.getDefaultInstance().get(url, new AsyncHttpClient.StringCallback() {
    @Override
    // Callback is invoked with any exceptions/errors, and the result, if available.
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


### AndroidAsync also let's you create simple HTTP servers:

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
