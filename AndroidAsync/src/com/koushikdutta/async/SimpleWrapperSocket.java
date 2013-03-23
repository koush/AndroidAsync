package com.koushikdutta.async;

import java.nio.ByteBuffer;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;

public class SimpleWrapperSocket implements AsyncSocketWrapper {
    AsyncSocket socket;
    public void setSocket(AsyncSocket socket) {
        this.socket = socket;
    }

    @Override
    public AsyncServer getServer() {
        return socket.getServer();
    }

    @Override
    public void setDataCallback(DataCallback callback) {
        socket.setDataCallback(callback);
    }

    @Override
    public DataCallback getDataCallback() {
        return socket.getDataCallback();
    }

    @Override
    public boolean isChunked() {
        return socket.isChunked();
    }

    @Override
    public void pause() {
        socket.pause();
    }

    @Override
    public void resume() {
        socket.resume();
    }

    @Override
    public boolean isPaused() {
        return socket.isPaused();
    }

    @Override
    public void setEndCallback(CompletedCallback callback) {
        socket.setEndCallback(callback);        
    }

    @Override
    public CompletedCallback getEndCallback() {
        return socket.getEndCallback();
    }

    @Override
    public void write(ByteBuffer bb) {
        socket.write(bb);
    }

    @Override
    public void write(ByteBufferList bb) {
        socket.write(bb);
    }

    @Override
    public void setWriteableCallback(WritableCallback handler) {
        socket.setWriteableCallback(handler);
    }

    @Override
    public WritableCallback getWriteableCallback() {
        return socket.getWriteableCallback();
    }

    @Override
    public boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    public void close() {
        socket.close();
    }

    @Override
    public void setClosedCallback(CompletedCallback handler) {
        socket.setClosedCallback(handler);
    }

    @Override
    public CompletedCallback getClosedCallback() {
        return socket.getClosedCallback();
    }

    @Override
    public AsyncSocket getSocket() {
        return socket;
    }
    
    @Override
    public DataEmitter getDataEmitter() {
        return socket;
    }
}
