package com.koushikdutta.async.test;

import android.support.test.runner.AndroidJUnit4;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
@RunWith(AndroidJUnit4.class)
public class CallbackTests {
    @Test
    public void testThrow() throws Exception {
        int port = AsyncServer.getDefault().listen(null, 0, new ListenCallback() {
            @Override
            public void onAccepted(AsyncSocket socket) {
                Util.writeAll(socket, "poop".getBytes(), new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {

                    }
                });

                socket.setDataCallback(new DataCallback.NullDataCallback());
            }

            @Override
            public void onListening(AsyncServerSocket socket) {

            }

            @Override
            public void onCompleted(Exception ex) {

            }
        }).getLocalPort();



        AsyncServer.getDefault().connectSocket("localhost", port, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                socket.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        throw new NullPointerException("this should crash?");
                    }
                });
            }
        });

        Thread.sleep(10000);
        fail();
    }
}
