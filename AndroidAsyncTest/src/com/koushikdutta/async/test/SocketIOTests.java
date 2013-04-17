package com.koushikdutta.async.test;

import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.SocketIOClient;
import com.koushikdutta.async.http.SocketIOClient.SocketIOConnectCallback;
import com.koushikdutta.async.http.SocketIOClient.StringCallback;

public class SocketIOTests extends TestCase {
    public static final long TIMEOUT = 10000L;
    
    
    class TriggerFuture extends SimpleFuture<Boolean> {
        public void trigger(boolean val) {
            setComplete(val);
        }
    }
    
    public void testEchoServer() throws Exception {
        final TriggerFuture trigger = new TriggerFuture();

        
        SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), "http://192.168.1.2:3000", new SocketIOConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient client) {
                assertNull(ex);
                client.setStringCallback(new StringCallback() {
                    @Override
                    public void onString(String string) {
                        trigger.trigger("hello".equals(string));
                    }
                });
                client.emit("hello");
            }
        });

        assertTrue(trigger.get(TIMEOUT * 10, TimeUnit.MILLISECONDS));
    }

}
