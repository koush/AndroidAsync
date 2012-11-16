package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Semaphore;

import junit.framework.Assert;
import android.os.Build;
import android.util.Log;

import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;

public class AsyncServer {
    private static final String LOGTAG = "NIO";
    
    static {
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
                java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
                java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
            }
        }
        catch (Throwable ex) {
        }
    }
    
    static AsyncServer mInstance = new AsyncServer() {
        {
            setAutostart(true);
        }
    };
    public static AsyncServer getDefault() {
        return mInstance;
    }
    
    private boolean mAutoStart = false;
    public void setAutostart(boolean autoStart) {
        mAutoStart = autoStart;
    }
    
    public boolean getAutoStart() {
        return mAutoStart;
    }
    
    private void autostart() {
        if (mAutoStart) {
            run(false, true);
        }
    }

    private Selector mSelector;

    public AsyncServer() {
    }

    private void handleSocket(final AsyncSocketImpl handler) throws ClosedChannelException {
        final ChannelWrapper sc = handler.getChannel();
        SelectionKey ckey = sc.register(mSelector);
        ckey.attach(handler);
        handler.mKey = ckey;
    }
    
    public void post(Runnable runnable) {
        synchronized (this) {
            mQueue.add(runnable);
            autostart();
            if (Thread.currentThread() != mAffinity) {
                if (mSelector != null)
                    mSelector.wakeup();
            }
            else {
                runQueue(mQueue);
            }
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
    
    private static void runQueue(LinkedList<Runnable> queue) {
        while (queue.size() > 0) {
            Runnable run = queue.remove();
            run.run();
        }
    }

    LinkedList<Runnable> mQueue = new LinkedList<Runnable>();

    public void stop() {
        synchronized (this) {
            if (mSelector == null)
                return;
            // replace the current queue with a new queue
            // and post a shutdown.
            // this is guaranteed to be the last job on the queue.
            final Selector currentSelector = mSelector;
            post(new Runnable() {
                @Override
                public void run() {
                    shutdownEverything(currentSelector);
                }
            });
            mQueue = new LinkedList<Runnable>();
            mSelector = null;
            mAffinity = null;
        }
    }
    
    protected void onDataTransmitted(int transmitted) {
    }
    
    public void listen(final InetAddress host, final int port, final ListenCallback handler) {
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    final ServerSocketChannel server = ServerSocketChannel.open();
                    final ServerSocketChannelWrapper wrapper = new ServerSocketChannelWrapper(server);
                    InetSocketAddress isa;
                    if (host == null)
                        isa = new InetSocketAddress(port);
                    else
                        isa = new InetSocketAddress(host, port);
                    server.socket().bind(isa);
                    SelectionKey key = wrapper.register(mSelector);
                    key.attach(handler);
                    handler.onListening(new AsyncServerSocket() {
                        @Override
                        public void stop() {
                            try {
                                server.close();
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
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
                        SelectionKey ckey = sc.register(mSelector);
                        ckey.attach(handler);
                        SocketAddress remote = new InetSocketAddress(host, port);
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

    public AsyncSocket connectDatagram(final SocketAddress remote) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncSocketImpl handler = new AsyncSocketImpl();
        handler.attach(socket);
        // ugh.. this should really be post to make it nonblocking...
        // but i want datagrams to be immediately writable.
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

    Thread mAffinity;
    public void run() {
        run(false, false);
    }
    public void run(final boolean keepRunning, boolean newThread) {
        final Selector selector;
        final LinkedList<Runnable> queue;
        synchronized (this) {
            if (mSelector != null) {
//                Log.i(LOGTAG, "Already running.");
                return;
            }
            try {
                selector = mSelector = SelectorProvider.provider().openSelector();
                queue = mQueue;
            }
            catch (IOException e) {
                return;
            }
            if (newThread) {
                mAffinity = new Thread() {
                    public void run() {
                        AsyncServer.run(AsyncServer.this, selector, queue, keepRunning);
                    };
                };
                mAffinity.start();
                // kicked off the new thread, let's bail.
                return;
            }
            mAffinity = Thread.currentThread();
            // fall through to outside of the synchronization scope
            // to allow the thread to run without locking.
        }

        run(this, selector, queue, keepRunning);
    }
    
    private static void run(AsyncServer server, Selector selector, LinkedList<Runnable> queue, boolean keepRunning) {
        // at this point, this local queue and selector are owned
        // by this thread.
        // if a stop is called, the instance queue and selector
        // will be replaced and nulled respectively.
        // this will allow the old queue and selector to shut down
        // gracefully, while also allowing a new selector thread
        // to start up while the old one is still shutting down.
        do {
            try {
                runLoop(server, selector, queue, keepRunning);
            }
            catch (Exception e) {
                Log.i(LOGTAG, "exception?");
                e.printStackTrace();
            }
        }
        while (selector.isOpen() && (selector.keys().size() > 0 || keepRunning));

        shutdownEverything(selector);
        synchronized (server) {
            if (server.mSelector == selector) {
                server.mQueue = new LinkedList<Runnable>();
                server.mSelector = null;
                server.mAffinity = null;
            }
        }
        Log.i(LOGTAG, "****AsyncServer has shut down.****");
    }
    
    private static void shutdownEverything(Selector selector) {
        for (SelectionKey key: selector.keys()) {
            try {
                key.cancel();
            }
            catch (Exception e) {
            }
        }

        // SHUT. DOWN. EVERYTHING.
        try {
            selector.close();
        }
        catch (Exception e) {
        }
    }

    private static void runLoop(AsyncServer server, Selector selector, LinkedList<Runnable> queue, boolean keepRunning) throws IOException {
//        Log.i(LOGTAG, "Keys: " + selector.keys().size());
        boolean needsSelect = true;
        synchronized (server) {
            // run the queue to populate the selector with keys
            runQueue(queue);
            // select now to see if anything is ready immediately. this
            // also clears the canceled key queue.
            int readyNow = selector.selectNow();
            if (readyNow == 0) {
                // if there is nothing to select now, make sure we don't have an empty key set
                // which means it would be time to turn this thread off.
                if (selector.keys().size() == 0 && !keepRunning) {
                    return;
                }
            }
            else {
                needsSelect = false;
            }
        }
        if (needsSelect) {
            // nothing to select immediately but there so let's block and wait.
            selector.select();
        }

        // process whatever keys are ready
        Set<SelectionKey> readyKeys = selector.selectedKeys();
        for (SelectionKey key : readyKeys) {
            try {
                if (key.isAcceptable()) {
                    ServerSocketChannel nextReady = (ServerSocketChannel) key.channel();
                    SocketChannel sc = nextReady.accept();
                    if (sc == null)
                        continue;
                    sc.configureBlocking(false);
                    SelectionKey ckey = sc.register(selector, SelectionKey.OP_READ);
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
                    server.onDataTransmitted(transmitted);
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
            catch (Exception ex) {
                Log.i(LOGTAG, "inner loop exception");
                ex.printStackTrace();
            }
        }
        readyKeys.clear();
    }
}
