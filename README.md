# AndroidAsync

### AndroidAsync is a java.nio based socket and http library for Android.
It uses java.nio to manage connections. All the connections are thus managed on a *single* thread, rather than one per thread. 
NIO is extremely efficient.


### Download a url to a String

```java
AsyncHttpClient.download(url, new AsyncHttpClient.StringCallback() {
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

