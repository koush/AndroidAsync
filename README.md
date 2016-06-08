# AndroidAsync

AndroidAsync is a low level network protocol library. If you are looking for an easy to use, higher level, Android aware,
http request library, check out [Ion](https://github.com/koush/ion) (it is built on top of AndroidAsync). The typical Android
app developer would probably be more interested in Ion.

But if you're looking for a raw Socket, HTTP client/server, WebSocket, and Socket.IO library for Android, AndroidAsync
is it.

#### Features
 * Based on NIO. One thread, driven by callbacks. Highly efficient.
 * All operations return a Future that can be cancelled
 * Socket client + socket server
 * HTTP client + server
 * WebSocket client + server
 * Socket.IO client

### Download

Download [the latest JAR](https://search.maven.org/remote_content?g=com.koushikdutta.async&a=androidasync&v=LATEST
) or grab via Maven:

```xml
<dependency>
    <groupId>com.koushikdutta.async</groupId>
    <artifactId>androidasync</artifactId>
    <version>(insert latest version)</version>
</dependency>
```

Gradle: 
```groovy
dependencies {
    compile 'com.koushikdutta.async:androidasync:2.+'
}
```

### Download a url to a String

```java
// url is the URL to download.
AsyncHttpClient.getDefaultInstance().getString(url, new AsyncHttpClient.StringCallback() {
    // Callback is invoked with any exceptions/errors, and the result, if available.
    @Override
    public void onCompleted(Exception e, AsyncHttpResponse response, String result) {
        if (e != null) {
            e.printStackTrace();
            return;
        }
        System.out.println("I got a string: " + result);
    }
});
```


### Download JSON from a url

```java
// url is the URL to download.
AsyncHttpClient.getDefaultInstance().getJSONObject(url, new AsyncHttpClient.JSONObjectCallback() {
    // Callback is invoked with any exceptions/errors, and the result, if available.
    @Override
    public void onCompleted(Exception e, AsyncHttpResponse response, JSONObject result) {
        if (e != null) {
            e.printStackTrace();
            return;
        }
        System.out.println("I got a JSONObject: " + result);
    }
});
```

Or for JSONArrays...

```java
// url is the URL to download.
AsyncHttpClient.getDefaultInstance().getJSONArray(url, new AsyncHttpClient.JSONArrayCallback() {
    // Callback is invoked with any exceptions/errors, and the result, if available.
    @Override
    public void onCompleted(Exception e, AsyncHttpResponse response, JSONArray result) {
        if (e != null) {
            e.printStackTrace();
            return;
        }
        System.out.println("I got a JSONArray: " + result);
    }
});
```


### Download a url to a file

```java
AsyncHttpClient.getDefaultInstance().getFile(url, filename, new AsyncHttpClient.FileCallback() {
    @Override
    public void onCompleted(Exception e, AsyncHttpResponse response, File result) {
        if (e != null) {
            e.printStackTrace();
            return;
        }
        System.out.println("my file is available at: " + result.getAbsolutePath());
    }
});
```



### Caching is supported too

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
            public void onDataAvailable(DataEmitter emitter, ByteBufferList byteBufferList) {
                System.out.println("I got some bytes!");
                // note that this data has been read
                byteBufferList.recycle();
            }
        });
    }
});
```


### AndroidAsync also supports socket.io (version 0.9.x)

```java
SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), "http://192.168.1.2:3000", new ConnectCallback() {
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
        client.on("someEvent", new EventCallback() {
            @Override
            public void onEvent(JSONArray argument, Acknowledge acknowledge) {
                System.out.println("args: " + arguments.toString());
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
AsyncHttpClient.getDefaultInstance().executeString(post, new AsyncHttpClient.StringCallback(){
        @Override
        public void onCompleted(Exception ex, AsyncHttpResponse source, String result) {
            if (ex != null) {
                ex.printStackTrace();
                return;
            }
            System.out.println("Server says: " + result);
        }
    });
```


### AndroidAsync also let's you create simple HTTP servers:

```java
AsyncHttpServer server = new AsyncHttpServer();

List<WebSocket> _sockets = new ArrayList<WebSocket>();

server.get("/", new HttpServerRequestCallback() {
    @Override
    public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        response.send("Hello!!!");
    }
});

// listen on port 5000
server.listen(5000);
// browsing http://localhost:5000 will return Hello!!!

```

### And WebSocket Servers:

```java
server.websocket("/live", new WebSocketRequestCallback() {
    @Override
    public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
        _sockets.add(webSocket);
        
        //Use this to clean up any references to your websocket
        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                try {
                    if (ex != null)
                        Log.e("WebSocket", "Error");
                } finally {
                    _sockets.remove(webSocket);
                }
            }
        });
        
        webSocket.setStringCallback(new StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                if ("Hello Server".equals(s))
                    webSocket.send("Welcome Client!");
            }
        });
    
    }
});

//..Sometime later, broadcast!
for (WebSocket socket : _sockets)
    socket.send("Fireball!");
```

### Futures

All the API calls return [Futures](http://en.wikipedia.org/wiki/Futures_and_promises).

```java
Future<String> string = client.getString("http://foo.com/hello.txt");
// this will block, and may also throw if there was an error!
String value = string.get();
```

Futures can also have callbacks...

```java
Future<String> string = client.getString("http://foo.com/hello.txt");
string.setCallback(new FutureCallback<String>() {
    @Override
    public void onCompleted(Exception e, String result) {
        System.out.println(result);
    }
});
```

For brevity...

```java
client.getString("http://foo.com/hello.txt")
.setCallback(new FutureCallback<String>() {
    @Override
    public void onCompleted(Exception e, String result) {
        System.out.println(result);
    }
});
```

### Note on SSLv3

https://github.com/koush/AndroidAsync/issues/174
