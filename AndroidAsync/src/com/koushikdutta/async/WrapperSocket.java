package com.koushikdutta.async;

import java.nio.ByteBuffer;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;

public class WrapperSocket implements AsyncSocket {
    private AsyncSocket mSocket;
    public void setSocket(AsyncSocket socket) {
        mSocket = socket;
    }
    
    public AsyncSocket getSocket() {
        return mSocket;
    }
    
    @Override
    public void setDataCallback(DataCallback callback) {
        mSocket.setDataCallback(callback);
    }

    @Override
    public DataCallback getDataCallback() {
        return mSocket.getDataCallback();
    }

    @Override
    public boolean isChunked() {
        return mSocket.isChunked();
    }

    @Override
    public void pause() {
        mSocket.pause();
    }

    @Override
    public void resume() {
        mSocket.resume();
    }

    @Override
    public boolean isPaused() {
        return mSocket.isPaused();
    }

    @Override
    public void setEndCallback(CompletedCallback callback) {
        mSocket.setEndCallback(callback);
    }

    @Override
    public CompletedCallback getEndCallback() {
        return mSocket.getEndCallback();
    }

    @Override
    public void write(ByteBuffer bb) {
        mSocket.write(bb);
    }

    @Override
    public void write(ByteBufferList bb) {
        mSocket.write(bb);
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        mSocket.setWriteableCallback(handler);
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return getWriteableCallback();
    }

    @Override
    public boolean isOpen() {
        return mSocket.isOpen();
    }

    @Override
    public void close() {
        mSocket.close();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        mSocket.setClosedCallback(handler);
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return mSocket.getClosedCallback();
    }

    @Override
    public AsyncServer getServer() {
        return mSocket.getServer();
    }

}
