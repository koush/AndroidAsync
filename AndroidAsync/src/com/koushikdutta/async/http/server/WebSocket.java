package com.koushikdutta.async.http.server;

import com.koushikdutta.async.CloseableData;
import com.koushikdutta.async.ExceptionEmitter;


public interface WebSocket extends CloseableData, ExceptionEmitter {
    static public interface StringCallback {
        public void onStringAvailable(String s);
    }
    
    static public interface DataCallback {
        public void onDataAvailable(byte[] data);
    }
    
    public void send(byte[] bytes);
    public void send(String string);
    
    public void setStringCallback(StringCallback callback);
    public void setDataCallback(DataCallback callback);
    public StringCallback getStringCallback();
    public DataCallback getDataCallback();
}
