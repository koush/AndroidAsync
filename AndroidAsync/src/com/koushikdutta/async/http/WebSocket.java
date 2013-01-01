package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;


public interface WebSocket extends AsyncSocket {
    static public interface StringCallback {
        public void onStringAvailable(String s);
    }
    
//    static public interface DataCallback {
//        public void onDataAvailable(byte[] data);
//    }
    
    public void send(byte[] bytes);
    public void send(String string);
    
    public void setStringCallback(StringCallback callback);
//    public void setDataCallback(DataCallback callback);
    public StringCallback getStringCallback();
//    public DataCallback getDataCallback();
    
    public boolean isBuffering();
    
    public AsyncSocket getSocket();
}
