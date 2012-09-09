package com.koushikdutta.async;

import java.io.IOException;
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
import java.util.concurrent.SynchronousQueue;

import junit.framework.Assert;
import android.util.Log;

import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;

public class AsyncServer {
    private static final String LOGTAG = "NIO";
    
    static AsyncServer mInstance = new AsyncServer();
    public static AsyncServer getDefault() {
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
        synchronized (this) {
            mQueue.add(runnable);
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

//    public void initialize() throws IOException {
//        synchronized (this) {
//            if (mSelector == null)
//                mSelector = SelectorProvider.provider().openSelector();
//        }
//    }

    Thread mAffinity;
    public void run() {
        run(false, false);
    }
    public void run(final boolean keepRunning, boolean newThread) {
        final Selector selector;
        final LinkedList<Runnable> queue;
        synchronized (this) {
            if (mSelector != null) {
                Log.i(LOGTAG, "Already running.");
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
        Log.i(LOGTAG, "****AsyncServer has shut down.****");
    }
    
    private static void shutdownEverything(Selector selector) {
        // SHUT. DOWN. EVERYTHING.
        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            }
            catch (IOException e) {
            }
        }

        try {
            selector.close();
        }
        catch (IOException e) {
        }
    }

    private static void runLoop(AsyncServer server, Selector selector, LinkedList<Runnable> queue, boolean keepRunning) throws IOException {
        synchronized (server) {
            // run the queue to populate the selector with keys
            runQueue(queue);
            // select now to see if anything is ready immediately. this
            // also clears the canceled key queue.
            int readyNow = selector.selectNow();
            if (readyNow == 0) {
                // if there is nothing to select now, make sure we don't have an empty key set
                // which means it would be time to turn this thread off.
                if (selector.keys().size() == 0) {
                    return;
                }
                // nothing to select immediately but there are keys available to select on,
                // so let's block and wait.
                selector.select();
            }
        }
        // process whatever keys are ready
        Set<SelectionKey> readyKeys = selector.selectedKeys();
        for (SelectionKey key : readyKeys) {
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
        readyKeys.clear();
    }
}
