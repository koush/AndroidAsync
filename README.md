# AndroidAsync

### AndroidAsync is a java.nio based socket and http library for Android.
It uses java.nio to manage connections. All the connections are thus managed on a *single* thread, rather than one per thread. 
NIO is extremely efficient.


### Download a url to a String

```java
// url is the URL to download. The callback will be invoked on the UI thread
// once the download is complete.
AsyncHttpClient.download(url, new AsyncHttpClient.StringCallback() {
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
AsyncHttpClient.download(url, filename, new AsyncHttpClient.FileCallback() {
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


### AndroidAsync also let's you create simple HTTP servers:

```java
AsyncHttpServer server = new AsyncHttpServer();
mServer.get(regex, new HttpServerRequestCallback() {
    @Override
    public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        response.send("Hello!!!");
    }
});
```
