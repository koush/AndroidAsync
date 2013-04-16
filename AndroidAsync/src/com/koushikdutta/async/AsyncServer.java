package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.SimpleCancelable;
import com.koushikdutta.async.future.SimpleFuture;

public class AsyncServer {
    public static final String LOGTAG = "NIO";
    public boolean mDebug = false;
    
    public static class AsyncSemaphore {
        Semaphore semaphore = new Semaphore(0);
        
        public void acquire() throws InterruptedException {
            ThreadQueue threadQueue = getOrCreateThreadQueue(Thread.currentThread());
            AsyncSemaphore last = threadQueue.waiter;
            threadQueue.waiter = this;
            Semaphore queueSemaphore = threadQueue.queueSemaphore;
            try {
                if (semaphore.tryAcquire())
                    return;
                
                while (true) {
                    // run the queue
                    while (true) {
                        Runnable run = threadQueue.remove();
                        if (run == null)
                            break;
//                        Log.i(LOGTAG, "Pumping for AsyncSemaphore");
                        run.run();
                    }

                    int permits = Math.max(1, queueSemaphore.availablePermits());
                    queueSemaphore.acquire(permits);
                    if (semaphore.tryAcquire())
                        break;
                }
            }
            finally {
                threadQueue.waiter = last;
            }
        }
        
        public boolean tryAcquire(long timeout, TimeUnit timeunit) throws InterruptedException {
            long timeoutMs = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
            ThreadQueue threadQueue = getOrCreateThreadQueue(Thread.currentThread());
            AsyncSemaphore last = threadQueue.waiter;
            threadQueue.waiter = this;
            Semaphore queueSemaphore = threadQueue.queueSemaphore;

            try {
                if (semaphore.tryAcquire())
                    return true;

                long start = System.currentTimeMillis();
                do {
                    // run the queue
                    while (true) {
                        Runnable run = threadQueue.remove();
                        if (run == null)
                            break;
//                        Log.i(LOGTAG, "Pumping for AsyncSemaphore");
                        run.run();
                    }

                    int permits = Math.max(1, queueSemaphore.availablePermits());
                    if (!queueSemaphore.tryAcquire(permits, timeoutMs, TimeUnit.MILLISECONDS))
                        return false;
                    if (semaphore.tryAcquire())
                        return true;
                }
                while (System.currentTimeMillis() - start < timeoutMs);
                return false;
            }
            finally {
                threadQueue.waiter = last;
            }
        }
        
        public void release() {
            semaphore.release();
            synchronized (mThreadQueues) {
                for (ThreadQueue threadQueue: mThreadQueues.values()) {
                    if (threadQueue.waiter == this)
                        threadQueue.queueSemaphore.release();
                }
            }
        }
    }
    
    static ThreadQueue getOrCreateThreadQueue(Thread thread) {
        ThreadQueue queue;
        synchronized (mThreadQueues) {
            queue = mThreadQueues.get(thread);
            if (queue == null) {
                queue = new ThreadQueue();
                mThreadQueues.put(thread, queue);
            }
        }
        
        return queue;
    }
    
    public static class ThreadQueue extends LinkedList<Runnable> {
        AsyncSemaphore waiter;
        Semaphore queueSemaphore = new Semaphore(0);

        @Override
        public boolean add(Runnable object) {
            synchronized (this) {
                return super.add(object);
            }
        }
        
        @Override
        public Runnable remove() {
            synchronized (this) {
                if (this.isEmpty())
                    return null;
                return super.remove();
            }
        }
    }
    private static WeakHashMap<Thread, ThreadQueue> mThreadQueues = new WeakHashMap<Thread, ThreadQueue>();
    public static void post(final Handler handler, final Runnable runnable) {
        ThreadQueue queue = getOrCreateThreadQueue(handler.getLooper().getThread());

        final ThreadQueue threadQueue = queue;
        Runnable wrapper = new Runnable() {
            boolean hasRun;
            @Override
            public void run() {
                synchronized (this) {
                    if (hasRun)
                        return;
                    hasRun = true;
                }
                try {
                    runnable.run();
                }
                finally {
                    threadQueue.remove(this);
                    handler.removeCallbacks(this);
                }
            }
        };
        
        threadQueue.add(wrapper);
        handler.post(wrapper);

        // run the queue if the thread is blocking
        threadQueue.queueSemaphore.release();
    }

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

    private Selector mSelector;

    public AsyncServer() {
    }

    private void handleSocket(final AsyncNetworkSocket handler) throws ClosedChannelException {
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
            // start the server up if necessary
            if (mSelector == null)
                run(false, true);
            if (Thread.currentThread() != mAffinity) {
                if (mSelector != null)
                    mSelector.wakeup();
            }
        }
        return s;
    }
    
    public Object post(Runnable runnable) {
        return postDelayed(runnable, 0);
    }
    
    public Object post(final CompletedCallback callback, final Exception e) {
        return post(new Runnable() {
            @Override
            public void run() {
                callback.onCompleted(e);
            }
        });
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
        Log.i(LOGTAG, "****AsyncServer is shutting down.****");
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
            synchronized (mServers) {
                mServers.remove(mAffinity);
            }
            mQueue = new LinkedList<Scheduled>();
            mSelector = null;
            mAffinity = null;
        }
//        final Selector currentSelector = mSelector;
//        run(new Runnable() {
//            @Override
//            public void run() {
//                synchronized (AsyncServer.this) {
//                    if (currentSelector == mSelector)
//                        shutdownEverything(currentSelector);
//                }
//            }
//        });
    }
    
    protected void onDataTransmitted(int transmitted) {
    }
    
    public void listen(final InetAddress host, final int port, final ListenCallback handler) {
        run(new Runnable() {
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
                            catch (Exception e) {
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
                    e.printStackTrace();
                    handler.onCompleted(e);
                }
            }
        });
    }
    
    private void connectSocketInternal(final SocketChannel socket, final SocketAddress remote, ConnectFuture cancel) {
        if (cancel.isCancelled())
            return;
        SelectionKey ckey = null;
        try {
            socket.configureBlocking(false);
            ckey = socket.register(mSelector, SelectionKey.OP_CONNECT);
            ckey.attach(cancel);
            socket.connect(remote);
        }
        catch (Exception e) {
            if (ckey != null)
                ckey.cancel();
            if (cancel.setComplete(e))
                cancel.callback.onConnectCompleted(e, null);
        }
    }
    
    private class ConnectFuture extends SimpleFuture<AsyncNetworkSocket> {
        @Override
        public boolean cancel() {
            if (!super.cancel())
                return false;

            post(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket.close();
                    }
                    catch (IOException e) {
                    }
                }
            });
            return true;
        }
        
        SocketChannel socket;
        ConnectCallback callback;
    }
    
    private ConnectFuture prepareConnectSocketCancelable(final SocketChannel socket, ConnectCallback handler) {
        ConnectFuture cancelable = new ConnectFuture();
        cancelable.socket = socket;
        cancelable.callback = handler;
        return cancelable;
    }
    
    public Cancellable connectSocket(final SocketAddress remote, final ConnectCallback handler) {
        try {
            final SocketChannel socket = SocketChannel.open();
            final ConnectFuture cancel = prepareConnectSocketCancelable(socket, handler);
            post(new Runnable() {
                @Override
                public void run() {
                    connectSocketInternal(socket, remote, cancel);
                }
            });
            return cancel;
        }
        catch (final Exception e) {
            post(new Runnable() {
                @Override
                public void run() {
                    handler.onConnectCompleted(e, null);
                }
            });
            return SimpleCancelable.COMPLETED;
        }
    }
    
    public Cancellable connectSocket(final String host, final int port, final ConnectCallback handler) {
        try {
            final SocketChannel socket = SocketChannel.open();
            final ConnectFuture cancel = prepareConnectSocketCancelable(socket, handler);

            post(new Runnable() {
                @Override
                public void run() {
                    SocketAddress remote;
                    try {
                        remote = new InetSocketAddress(host, port);
                    }
                    catch (Exception e) {
                        if (cancel.setComplete(e))
                            handler.onConnectCompleted(e, null);
                        return;
                    }

                    connectSocketInternal(socket, remote, cancel);
                }
            });
            
            return cancel;
        }
        catch (final Exception e) {
            post(new Runnable() {
                @Override
                public void run() {
                    handler.onConnectCompleted(e, null);
                }
            });
            return SimpleCancelable.COMPLETED;
        }
    }

    public AsyncSocket connectDatagram(final SocketAddress remote) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncNetworkSocket handler = new AsyncNetworkSocket();
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
    
    static Hashtable<Thread, AsyncServer> mServers = new Hashtable<Thread, AsyncServer>();

    private boolean addMe() {
        synchronized (mServers) {
            AsyncServer current = mServers.get(Thread.currentThread());
            if (current != null) {
                Log.e(LOGTAG, "****AsyncServer already running on this thread.****");
                return false;
            }
            mServers.put(mAffinity, this);
        }
        return true;
    }
    
    public static AsyncServer getCurrentThreadServer() {
        return mServers.get(Thread.currentThread());
    }
    
    Thread mAffinity;
    public void run() {
        run(false, false);
    }
    public void run(final boolean keepRunning, boolean newThread) {
        final Selector selector;
        final LinkedList<Scheduled> queue;
        boolean reentrant = false;
        synchronized (this) {
            if (mSelector != null) {
                Log.i(LOGTAG, "Reentrant call");
                Assert.assertEquals(Thread.currentThread(), mAffinity);
                // this is reentrant
                reentrant = true;
                selector = mSelector;
                queue = mQueue;
            }
            else {
                try {
                    selector = mSelector = SelectorProvider.provider().openSelector();
                    queue = mQueue;
                }
                catch (IOException e) {
                    return;
                }
                if (newThread) {
                    mAffinity = new Thread("AsyncServer") {
                        public void run() {
                            AsyncServer.run(AsyncServer.this, selector, queue, keepRunning);
                        };
                    };
                }
                else {
                    mAffinity = Thread.currentThread();
                }
                if (!addMe()) {
                    try {
                        mSelector.close();
                    }
                    catch (Exception e) {
                    }
                    mSelector = null;
                    mAffinity = null;
                    return;
                }
                if (newThread) {
                    mAffinity.start();
                    // kicked off the new thread, let's bail.
                    return;
                }

                // fall through to outside of the synchronization scope
                // to allow the thread to run without locking.
            }
        }

        if (reentrant) {
            try {
                runLoop(this, selector, queue, false);
            }
            catch (Exception e) {
                Log.i(LOGTAG, "exception?");
                e.printStackTrace();
            }
            return;
        }
        
        run(this, selector, queue, keepRunning);
    }
    
    private static void run(AsyncServer server, Selector selector, LinkedList<Scheduled> queue, boolean keepRunning) {
        Log.i(LOGTAG, "****AsyncServer is starting.****");
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
            catch (ClosedSelectorException e) {
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
        synchronized (mServers) {
            mServers.remove(Thread.currentThread());
        }
        Log.i(LOGTAG, "****AsyncServer has shut down.****");
    }
    
    private static void shutdownEverything(Selector selector) {
        try {
            for (SelectionKey key: selector.keys()) {
                try {
                    key.channel().close();
                }
                catch (Exception e) {
                }
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
    
    private static final long DEFAULT_WAIT = 100;
    private static long lockAndRunQueue(AsyncServer server, LinkedList<Scheduled> queue) {
        long wait = DEFAULT_WAIT;
        
        // find the first item we can actually run
        while (true) {
            Scheduled run = null;

            synchronized (server) {
                long now = System.currentTimeMillis();
                LinkedList<Scheduled> later = null;

                while (queue.size() > 0) {
                    Scheduled s = queue.remove();
                    if (s.time <= now) {
                        run = s;
                        break;
                    }
                    else {
                        wait = Math.min(wait, s.time - now);
                        if (later == null)
                            later = new LinkedList<AsyncServer.Scheduled>();
                        later.add(s);
                    }
                }
                if (later != null)
                    queue.addAll(later);
            }
            
            if (run == null)
                break;
            
            run.runnable.run();
        }

        return wait;
    }

    private static void runLoop(AsyncServer server, Selector selector, LinkedList<Scheduled> queue, boolean keepRunning) throws IOException {
//        Log.i(LOGTAG, "Keys: " + selector.keys().size());
        boolean needsSelect = true;

        // run the queue to populate the selector with keys
        long wait = lockAndRunQueue(server, queue);
        synchronized (server) {
            // select now to see if anything is ready immediately. this
            // also clears the canceled key queue.
            int readyNow = selector.selectNow();
            if (readyNow == 0) {
                // if there is nothing to select now, make sure we don't have an empty key set
                // which means it would be time to turn this thread off.
                if (selector.keys().size() == 0 && !keepRunning) {
//                    Log.i(LOGTAG, "Shutting down. keys: " + selector.keys().size() + " keepRunning: " + keepRunning);
                    return;
                }
            }
            else {
                needsSelect = false;
            }
        }        

        if (needsSelect) {
            // nothing to select immediately but there so let's block and wait.
            selector.select(wait);
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
                    AsyncNetworkSocket handler = new AsyncNetworkSocket();
                    handler.attach(sc);
                    handler.setup(server, ckey);
                    ckey.attach(handler);
                    serverHandler.onAccepted(handler);
                }
                else if (key.isReadable()) {
                    AsyncNetworkSocket handler = (AsyncNetworkSocket) key.attachment();
                    int transmitted = handler.onReadable();
                    server.onDataTransmitted(transmitted);
                }
                else if (key.isWritable()) {
                    AsyncNetworkSocket handler = (AsyncNetworkSocket) key.attachment();
                    handler.onDataWritable();
                }
                else if (key.isConnectable()) {
                    ConnectFuture cancel = (ConnectFuture) key.attachment();
                    SocketChannel sc = (SocketChannel) key.channel();
                    key.interestOps(SelectionKey.OP_READ);
                    try {
                        sc.finishConnect();
                        AsyncNetworkSocket newHandler = new AsyncNetworkSocket();
                        newHandler.setup(server, key);
                        newHandler.attach(sc);
                        key.attach(newHandler);
                        if (cancel.setComplete(newHandler))
                            cancel.callback.onConnectCompleted(null, newHandler);
                    }
                    catch (Exception ex) {
                        key.cancel();
                        sc.close();
                        if (cancel.setComplete())
                            cancel.callback.onConnectCompleted(ex, null);
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
