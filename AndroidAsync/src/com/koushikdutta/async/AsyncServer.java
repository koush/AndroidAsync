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

import junit.framework.Assert;
import android.os.Build;
import android.util.Log;

import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;

public class AsyncServer {
    public static final String LOGTAG = "NIO";
    
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
        handler.setup(this, ckey);
    }
    
    public void removeAllCallbacks(Object scheduled) {
        synchronized (this) {
            mQueue.remove(scheduled);
        }
    }
    
    public Object postDelayed(Runnable runnable, long delay) {
        Scheduled s;
        synchronized (this) {
            if (delay != 0)
                delay += System.currentTimeMillis();
            mQueue.add(s = new Scheduled(runnable, delay));
            autostart();
            if (Thread.currentThread() != mAffinity) {
                if (mSelector != null)
                    mSelector.wakeup();
            }
            else {
//                runQueue(mQueue);
            }
        }
        return s;
    }
    
    public Object post(Runnable runnable) {
        return postDelayed(runnable, 0);
    }
    
    public void run(final Runnable runnable) {
        if (Thread.currentThread() == mAffinity) {
            post(runnable);
            lockAndRunQueue(this, mQueue);
            return;
        }

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
    
    private static void runQueue(LinkedList<Scheduled> queue) {
        long now = System.currentTimeMillis();
        LinkedList<Scheduled> later = null;
        while (queue.size() > 0) {
            Scheduled s = queue.remove();
            if (s.time < now)
                s.runnable.run();
            else {
                if (later == null)
                    later = new LinkedList<AsyncServer.Scheduled>();
                later.add(s);
            }
        }
        if (later != null)
            queue.addAll(later);
    }

    private static class Scheduled {
        public Scheduled(Runnable runnable, long time) {
            this.runnable = runnable;
            this.time = time;
        }
        public Runnable runnable;
        public long time;
    }
    LinkedList<Scheduled> mQueue = new LinkedList<Scheduled>();

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
            mQueue = new LinkedList<Scheduled>();
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
                    final SelectionKey key = wrapper.register(mSelector);
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
                            try {
                                key.cancel();
                            }
                            catch (Exception e) {
                            }
                        }
                    });
                }
                catch (Exception e) {
                    handler.onCompleted(e);
                    e.printStackTrace();
                }
            }
        });
    }
    
    private void connectSocketInternal(final SocketChannel socket, ChannelWrapper sc, final SocketAddress remote, final ConnectCallback handler, final SimpleCancelable cancel) {
        synchronized (cancel) {
            if (cancel.isCanceled())
                return;
            SelectionKey ckey = null;
            try {
                ckey = sc.register(mSelector);
                ckey.attach(handler);
                socket.connect(remote);
            }
            catch (Exception e) {
                if (ckey != null)
                    ckey.cancel();
                handler.onConnectCompleted(e, null);
            }
        }
    }
    
    private SimpleCancelable prepareConnectSocketCancelable(final SocketChannel socket, final ChannelWrapper sc) {
        return new SimpleCancelable() {
            @Override
            public Cancelable cancel() {
                synchronized (this) {
                    super.cancel();
                    try {
                        sc.close();
                    }
                    catch (IOException e) {
                    }
                    return this;
                }
            }
        };
    }
    
    public Cancelable connectSocket(final SocketAddress remote, final ConnectCallback handler) {
        try {
            final SocketChannel socket = SocketChannel.open();
            final ChannelWrapper sc = new SocketChannelWrapper(socket);
            final SimpleCancelable cancel = prepareConnectSocketCancelable(socket, sc);
            post(new Runnable() {
                @Override
                public void run() {
                    connectSocketInternal(socket, sc, remote, handler, cancel);
                }
            });
            return cancel;
        }
        catch (Exception e) {
            handler.onConnectCompleted(e, null);
            return SimpleCancelable.COMPLETED;
        }
    }
    
    public Cancelable connectSocket(final String host, final int port, final ConnectCallback handler) {
        try {
            final SocketChannel socket = SocketChannel.open();
            final ChannelWrapper sc = new SocketChannelWrapper(socket);
            final SimpleCancelable cancel = prepareConnectSocketCancelable(socket, sc);

            post(new Runnable() {
                @Override
                public void run() {
                    SocketAddress remote;
                    try {
                        remote = new InetSocketAddress(host, port);
                    }
                    catch (Exception e) {
                        cancel.setComplete(true);
                        handler.onConnectCompleted(e, null);
                        return;
                    }

                    connectSocketInternal(socket, sc, remote, handler, cancel);
                }
            });
            
            return cancel;
        }
        catch (Exception e) {
            handler.onConnectCompleted(e, null);
            return SimpleCancelable.COMPLETED;
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
        final LinkedList<Scheduled> queue;
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
    
    private static void run(AsyncServer server, Selector selector, LinkedList<Scheduled> queue, boolean keepRunning) {
        // at this point, this local queue and selector are owned
        // by this thread.
        // if a stop is called, the instance queue and selector
        // will be replaced and nulled respectively.
        // this will allow the old queue and selector to shut down
        // gracefully, while also allowing a new selector thread
        // to start up while the old one is still shutting down.
        while(true) {
            try {
                runLoop(server, selector, queue, keepRunning);
            }
            catch (Exception e) {
                Log.i(LOGTAG, "exception?");
                e.printStackTrace();
            }
            // see if we keep looping, this must be in a synchronized block since the queue is accessed.
            synchronized (server) {
                if (selector.isOpen() && (selector.keys().size() > 0 || keepRunning || queue.size() > 0))
                    continue;

                shutdownEverything(selector);
                if (server.mSelector == selector) {
                    server.mQueue = new LinkedList<Scheduled>();
                    server.mSelector = null;
                    server.mAffinity = null;
                }
                break;
            }
        }
        Log.i(LOGTAG, "****AsyncServer has shut down.****");
    }
    
    private static void shutdownEverything(Selector selector) {
        try {
            for (SelectionKey key: selector.keys()) {
                try {
                    key.cancel();
                }
                catch (Exception e) {
                }
            }
        }
        catch (Exception ex) {
        }

        // SHUT. DOWN. EVERYTHING.
        try {
            selector.close();
        }
        catch (Exception e) {
        }
    }
    
    private static void lockAndRunQueue(AsyncServer server, LinkedList<Scheduled> queue) {
        LinkedList<Scheduled> copy;
        synchronized (server) {
            copy = new LinkedList<Scheduled>(queue);
            queue.clear();
        }
        runQueue(copy);
    }

    private static void runLoop(AsyncServer server, Selector selector, LinkedList<Scheduled> queue, boolean keepRunning) throws IOException {
//        Log.i(LOGTAG, "Keys: " + selector.keys().size());
        boolean needsSelect = true;

        // run the queue to populate the selector with keys
        lockAndRunQueue(server, queue);
        synchronized (server) {
            // select now to see if anything is ready immediately. this
            // also clears the canceled key queue.
            int readyNow = selector.selectNow();
            if (readyNow == 0) {
                // if there is nothing to select now, make sure we don't have an empty key set
                // which means it would be time to turn this thread off.
                if (selector.keys().size() == 0 && !keepRunning) {
                    Log.i(LOGTAG, "Shutting down. keys: " + selector.keys().size() + " keepRunning: " + keepRunning);
                    return;
                }
            }
            else {
                needsSelect = false;
            }
        }        

        if (needsSelect) {
            // nothing to select immediately but there so let's block and wait.
            selector.select(100);
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
                    handler.setup(server, ckey);
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
                        newHandler.setup(server, key);
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
    
    public Thread getAffinity() {
        return mAffinity;
    }
    
    public boolean isAffinityThread() {
        return mAffinity == Thread.currentThread();
    }
}
