package com.koushikdutta.async;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Semaphore;

import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;

import junit.framework.Assert;
import android.util.Log;

public class AsyncServer {
    private static final String LOGTAG = "NIO";
    
    static AsyncServer mInstance = new AsyncServer();
    public static AsyncServer getDefault() {
        if (mInstance == null)
            mInstance = new AsyncServer();
        
        if (!mInstance.mRun) {
            try {
                mInstance.initialize();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            new Thread() {
                @Override
                public void run() {
                    mInstance.run();
                }
            }.start();
        }

        return mInstance;
    }

    Selector mSelector;

    public AsyncServer() {
    }

    private void handleSocket(final AsyncSocketImpl handler) throws ClosedChannelException {
        final ChannelWrapper sc = handler.getChannel();
        SelectionKey ckey = sc.register(mSelector);
        ckey.attach(handler);
        handler.mKey = ckey;
    }
    
    public void post(Runnable runnable) {
        synchronized (mQueue) {
            mQueue.add(runnable);
        }
        if (Thread.currentThread() == mAffinity) {
            runQueue();
        }
        else {
            mSelector.wakeup();
        }
    }
    
    public void run(final Runnable runnable) {
        final Semaphore semaphore = new Semaphore(0);
        post(new Runnable() {
            @Override
            public void run() {
                runnable.run();
                semaphore.release();
            }
        });
        try {
            semaphore.acquire();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private void runQueue() {
        Assert.assertEquals(Thread.currentThread(), mAffinity);
        synchronized (mQueue) {
            while (mQueue.size() > 0) {
                Runnable run = mQueue.remove();
                run.run();
            }
        }
    }

    boolean mRun = false;
    boolean mShuttingDown = false;
    LinkedList<Runnable> mQueue = new LinkedList<Runnable>();

    public void stop() {
        mRun = false;
        mShuttingDown = true;
        mSelector.wakeup();
    }
    
    protected void onDataTransmitted(int transmitted) {
    }
    
    public void listen(final InetAddress host, final int port, final ListenCallback handler) {
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocketChannel server = ServerSocketChannel.open();
                    final ServerSocketChannelWrapper wrapper = new ServerSocketChannelWrapper(server);
                    InetSocketAddress isa;
                    if (host == null)
                        isa = new InetSocketAddress(port);
                    else
                        isa = new InetSocketAddress(host, port);
                    server.socket().bind(isa);
                    SelectionKey key = wrapper.register(mSelector);
                    key.attach(handler);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    public void connectSocket(final SocketAddress remote, final ConnectCallback handler) {
        try {
            final SocketChannel socket = SocketChannel.open();
            final ChannelWrapper sc = new SocketChannelWrapper(socket);
            post(new Runnable() {
                @Override
                public void run() {
                    try {
                        SelectionKey ckey = sc.register(mSelector);
                        ckey.attach(handler);
                        socket.connect(remote);
                    }
                    catch (Exception e) {
                        handler.onConnectCompleted(e, null);
                    }
                }
            });
        }
        catch (Exception e) {
            handler.onConnectCompleted(e, null);
        }
    }
    
    public void connectSocket(final String host, final int port, final ConnectCallback handler) {
        try {
            final SocketChannel socket = SocketChannel.open();
            final ChannelWrapper sc = new SocketChannelWrapper(socket);
            post(new Runnable() {
                @Override
                public void run() {
                    try {
                        SocketAddress remote = new InetSocketAddress(host, port);
                        socket.connect(remote);
                        SelectionKey ckey = sc.register(mSelector);
                        ckey.attach(handler);
                    }
                    catch (Exception e) {
                        handler.onConnectCompleted(e, null);
                    }
                }
            });
        }
        catch (Exception e) {
            handler.onConnectCompleted(e, null);
        }
    }

    public AsyncSocket connectDatagram(final SocketAddress remote) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncSocketImpl handler = new AsyncSocketImpl();
        handler.attach(socket);
        // ugh.. this should really be post to make it nonblocking...
        // but i want datagrams to be immediately writreable.
        // they're not really used anyways.
        run(new Runnable() {
            @Override
            public void run() {
                try {
                    handleSocket(handler);
                    socket.connect(remote);
                }
                catch (Exception e) {
                }
            }
        });
        return handler;
    }

    public void initialize() throws IOException {
        synchronized (this) {
            if (mSelector == null)
                mSelector = SelectorProvider.provider().openSelector();
        }
    }

    Thread mAffinity;
    public void run() {
        synchronized (this) {
            if (mRun) {
                Log.i(LOGTAG, "Already running.");
                return;
            }
            if (mShuttingDown) {
                Log.i(LOGTAG, "Shutdown in progress.");
                return;
            }
            mRun = true;
            mAffinity = Thread.currentThread();
        }

        while (mRun) {
            try {
                runLoop();
                if (mSelector.keys().size() == 0)
                    mRun = false;
            }
            catch (Exception e) {
                Log.i(LOGTAG, "exception?");
                e.printStackTrace();
            }
        }

        // SHUT. DOWN. EVERYTHING.
        for (SelectionKey key : mSelector.keys()) {
            try {
                key.channel().close();
            }
            catch (IOException e) {
            }
        }

        try {
            mSelector.close();
        }
        catch (IOException e) {
        }
        mSelector = null;
        mShuttingDown = false;
        mAffinity = null;
        Log.i(LOGTAG, "****AsyncServer has shut down.****");
    }

    private void runLoop() throws IOException {
        runQueue();
        int readyNow = mSelector.selectNow();
        if (readyNow == 0) {
            if (mSelector.keys().size() == 0)
                return;
            mSelector.select();
        }
        Set<SelectionKey> readyKeys = mSelector.selectedKeys();
        for (SelectionKey key : readyKeys) {
            if (key.isAcceptable()) {
                ServerSocketChannel nextReady = (ServerSocketChannel) key.channel();
                SocketChannel sc = nextReady.accept();
                if (sc == null)
                    continue;
                sc.configureBlocking(false);
                SelectionKey ckey = sc.register(mSelector, SelectionKey.OP_READ);
                ListenCallback serverHandler = (ListenCallback) key.attachment();
                AsyncSocketImpl handler = new AsyncSocketImpl();
                handler.attach(sc);
                handler.mKey = ckey;
                ckey.attach(handler);
                serverHandler.onAccepted(handler);
            }
            else if (key.isReadable()) {
                AsyncSocketImpl handler = (AsyncSocketImpl) key.attachment();
                int transmitted = handler.onReadable();
                onDataTransmitted(transmitted);
            }
            else if (key.isWritable()) {
                AsyncSocketImpl handler = (AsyncSocketImpl) key.attachment();
                handler.onDataWritable();
            }
            else if (key.isConnectable()) {
                ConnectCallback handler = (ConnectCallback) key.attachment();
                SocketChannel sc = (SocketChannel) key.channel();
                key.interestOps(SelectionKey.OP_READ);
                try {
                    sc.finishConnect();
                    AsyncSocketImpl newHandler = new AsyncSocketImpl();
                    newHandler.mKey = key;
                    newHandler.attach(sc);
                    key.attach(newHandler);
                    handler.onConnectCompleted(null, newHandler);
                }
                catch (Exception ex) {
                    key.cancel();
                    sc.close();
                    handler.onConnectCompleted(ex, null);
                }
            }
            else {
                Log.i(LOGTAG, "wtf");
                Assert.fail();
            }
        }
        readyKeys.clear();
    }
}
