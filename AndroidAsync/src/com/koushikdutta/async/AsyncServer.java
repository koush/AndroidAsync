package com.koushikdutta.async;

import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.util.StreamUtility;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

public class AsyncServer {
    public static final String LOGTAG = "NIO";

    private static class RunnableWrapper implements Runnable {
        boolean hasRun;
        Runnable runnable;
        ThreadQueue threadQueue;
        Handler handler;
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

                threadQueue = null;
                handler = null;
                runnable = null;
            }
        }
    }
    public static void post(Handler handler, Runnable runnable) {
        RunnableWrapper wrapper = new RunnableWrapper();
        ThreadQueue threadQueue = ThreadQueue.getOrCreateThreadQueue(handler.getLooper().getThread());
        wrapper.threadQueue = threadQueue;
        wrapper.handler = handler;
        wrapper.runnable = runnable;

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
    
    static AsyncServer mInstance = new AsyncServer();
    public static AsyncServer getDefault() {
        return mInstance;
    }

    private SelectorWrapper mSelector;

    public boolean isRunning() {
        return mSelector != null;
    }

    String mName;
    public AsyncServer() {
        this(null);
    }

    public AsyncServer(String name) {
        if (name == null)
            name = "AsyncServer";
        mName = name;
    }

    private void handleSocket(final AsyncNetworkSocket handler) throws ClosedChannelException {
        final ChannelWrapper sc = handler.getChannel();
        SelectionKey ckey = sc.register(mSelector.getSelector());
        ckey.attach(handler);
        handler.setup(this, ckey);
    }
    
    public void removeAllCallbacks(Object scheduled) {
        synchronized (this) {
            mQueue.remove(scheduled);
        }
    }

    private static void wakeup(final SelectorWrapper selector) {
        if (!synchronousWorkers.isShutdown()) {
            synchronousWorkers.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        selector.wakeupOnce();
                    } catch (Exception e) {
                        Log.i(LOGTAG, "Selector Exception? L Preview?");
                    }
                }
            });
        } else {
	        Log.i(LOGTAG, "Executor shutdown");
        }
    }
    
    public Object postDelayed(Runnable runnable, long delay) {
        Scheduled s;
        synchronized (this) {
            // Calculate when to run this queue item:
            // If there is a delay (non-zero), add it to the current time
            // When delay is zero, ensure that this follows all other
            // zero-delay queue items. This is done by setting the
            // "time" to the queue size. This will make sure it is before
            // all time-delayed queue items (for all real world scenarios)
            // as it will always be less than the current time and also remain
            // behind all other immediately run queue items.
            long time;
            if (delay != 0)
                time = System.currentTimeMillis() + delay;
            else
                time = mQueue.size();
            mQueue.add(s = new Scheduled(runnable, time));
            // start the server up if necessary
            if (mSelector == null)
                run(true);
            if (!isAffinityThread()) {
                wakeup(mSelector);
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
            Log.e(LOGTAG, "run", e);
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
    PriorityQueue<Scheduled> mQueue = new PriorityQueue<Scheduled>(1, Scheduler.INSTANCE);

    static class Scheduler implements Comparator<Scheduled> {
        public static Scheduler INSTANCE = new Scheduler();
        private Scheduler() {
        }
        @Override
        public int compare(Scheduled s1, Scheduled s2) {
            // keep the smaller ones at the head, so they get tossed out quicker
            if (s1.time == s2.time)
                return 0;
            if (s1.time > s2.time)
                return 1;
            return -1;
        }
    }


    public void stop() {
//        Log.i(LOGTAG, "****AsyncServer is shutting down.****");
        final SelectorWrapper currentSelector;
        final Semaphore semaphore;
        final boolean isAffinityThread;
        synchronized (this) {
            isAffinityThread = isAffinityThread();
            currentSelector = mSelector;
            if (currentSelector == null)
                return;
            synchronized (mServers) {
                mServers.remove(mAffinity);
            }
            semaphore = new Semaphore(0);

            // post a shutdown and wait
            mQueue.add(new Scheduled(new Runnable() {
                @Override
                public void run() {
                    shutdownEverything(currentSelector);
                    semaphore.release();
                }
            }, 0));
            currentSelector.wakeupOnce();

            // force any existing connections to die
            shutdownKeys(currentSelector);

            mQueue = new PriorityQueue<Scheduled>(1, Scheduler.INSTANCE);
            mSelector = null;
            mAffinity = null;
        }
        try {
            if (!isAffinityThread)
                semaphore.acquire();
        }
        catch (Exception e) {
        }
    }
    
    protected void onDataReceived(int transmitted) {
    }

    protected void onDataSent(int transmitted) {
    }

    private static class ObjectHolder<T> {
        T held;
    }
    public AsyncServerSocket listen(final InetAddress host, final int port, final ListenCallback handler) {
        final ObjectHolder<AsyncServerSocket> holder = new ObjectHolder<AsyncServerSocket>();
        run(new Runnable() {
            @Override
            public void run() {
                ServerSocketChannel closeableServer = null;
                ServerSocketChannelWrapper closeableWrapper = null;
                try {
                    closeableServer = ServerSocketChannel.open();
                    closeableWrapper = new ServerSocketChannelWrapper(
                            closeableServer);
                    final ServerSocketChannel server = closeableServer;
                    final ServerSocketChannelWrapper wrapper = closeableWrapper;
                    InetSocketAddress isa;
                    if (host == null)
                        isa = new InetSocketAddress(port);
                    else
                        isa = new InetSocketAddress(host, port);
                    server.socket().bind(isa);
                    final SelectionKey key = wrapper.register(mSelector.getSelector());
                    key.attach(handler);
                    handler.onListening(holder.held = new AsyncServerSocket() {
                        @Override
                        public int getLocalPort() {
                            return server.socket().getLocalPort();
                        }

                        @Override
                        public void stop() {
                            StreamUtility.closeQuietly(wrapper);
                            try {
                                key.cancel();
                            }
                            catch (Exception e) {
                            }
                        }
                    });
                }
                catch (IOException e) {
                    StreamUtility.closeQuietly(closeableWrapper, closeableServer);
                    handler.onCompleted(e);
                }
            }
        });
        return holder.held;
    }

    private class ConnectFuture extends SimpleFuture<AsyncNetworkSocket> {
        @Override
        protected void cancelCleanup() {
            super.cancelCleanup();
            try {
                if (socket != null)
                    socket.close();
            }
            catch (IOException e) {
            }
        }

        SocketChannel socket;
        ConnectCallback callback;
    }
    
    private ConnectFuture connectResolvedInetSocketAddress(final InetSocketAddress address, final ConnectCallback callback) {
        final ConnectFuture cancel = new ConnectFuture();
        assert !address.isUnresolved();

        post(new Runnable() {
            @Override
            public void run() {
                if (cancel.isCancelled())
                    return;

                cancel.callback = callback;
                SelectionKey ckey = null;
                SocketChannel socket = null;
                try {
                    socket = cancel.socket = SocketChannel.open();
                    socket.configureBlocking(false);
                    ckey = socket.register(mSelector.getSelector(), SelectionKey.OP_CONNECT);
                    ckey.attach(cancel);
                    socket.connect(address);
                }
                catch (IOException e) {
                    if (ckey != null)
                        ckey.cancel();
                    StreamUtility.closeQuietly(socket);
                    cancel.setComplete(e);
                }
            }
        });

        return cancel;
    }

    public Cancellable connectSocket(final InetSocketAddress remote, final ConnectCallback callback) {
        if (!remote.isUnresolved())
            return connectResolvedInetSocketAddress(remote, callback);

        final SimpleFuture<AsyncNetworkSocket> ret = new SimpleFuture<AsyncNetworkSocket>();

        Future<InetAddress> lookup = getByName(remote.getHostName());
        ret.setParent(lookup);
        lookup
        .setCallback(new FutureCallback<InetAddress>() {
            @Override
            public void onCompleted(Exception e, InetAddress result) {
                if (e != null) {
                    callback.onConnectCompleted(e, null);
                    ret.setComplete(e);
                    return;
                }

                ret.setComplete(connectResolvedInetSocketAddress(new InetSocketAddress(result, remote.getPort()), callback));
            }
        });
        return ret;
    }

    public Cancellable connectSocket(final String host, final int port, final ConnectCallback callback) {
        return connectSocket(InetSocketAddress.createUnresolved(host, port), callback);
    }

    private static ExecutorService synchronousWorkers = Executors.newFixedThreadPool(4);
    public Future<InetAddress[]> getAllByName(final String host) {
        final SimpleFuture<InetAddress[]> ret = new SimpleFuture<InetAddress[]>();
        if (!synchronousWorkers.isShutdown()) {
            synchronousWorkers.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final InetAddress[] result = InetAddress.getAllByName(host);
                        if (result == null || result.length == 0)
                            throw new HostnameResolutionException("no addresses for host");
                        post(new Runnable() {
                            @Override
                            public void run() {
                                ret.setComplete(null, result);
                            }
                        });
                    } catch (final Exception e) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                ret.setComplete(e, null);
                            }
                        });
                    }
                }
            });
        } else {
	        Log.i(LOGTAG, "Executor shutdown");
	        ret.setComplete(new RejectedExecutionException("Executor shutdown"), null);
        }
        return ret;
    }

    public Future<InetAddress> getByName(String host) {
        return getAllByName(host)
        .then(new TransformFuture<InetAddress, InetAddress[]>() {
            @Override
            protected void transform(InetAddress[] result) throws Exception {
                setComplete(result[0]);
            }
        });
    }

    public AsyncDatagramSocket connectDatagram(final String host, final int port) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
        handler.attach(socket);
        // ugh.. this should really be post to make it nonblocking...
        // but i want datagrams to be immediately writable.
        // they're not really used anyways.
        run(new Runnable() {
            @Override
            public void run() {
                try {
                    final SocketAddress remote = new InetSocketAddress(host, port);
                    handleSocket(handler);
                    socket.connect(remote);
                }
                catch (IOException e) {
                    Log.e(LOGTAG, "Datagram error", e);
                    StreamUtility.closeQuietly(socket);
                }
            }
        });
        return handler;
    }

    public AsyncDatagramSocket openDatagram() throws IOException {
        return openDatagram(null, false);
    }

    public AsyncDatagramSocket openDatagram(final SocketAddress address, final boolean reuseAddress) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
        handler.attach(socket);
        // ugh.. this should really be post to make it nonblocking...
        // but i want datagrams to be immediately writable.
        // they're not really used anyways.
        run(new Runnable() {
            @Override
            public void run() {
                try {
                    if (reuseAddress)
                        socket.socket().setReuseAddress(reuseAddress);
                    socket.socket().bind(address);
                    handleSocket(handler);
                }
                catch (IOException e) {
                    Log.e(LOGTAG, "Datagram error", e);
                    StreamUtility.closeQuietly(socket);
                }
            }
        });
        return handler;
    }
    
    public AsyncDatagramSocket connectDatagram(final SocketAddress remote) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
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
                catch (IOException e) {
                    StreamUtility.closeQuietly(socket);
                }
            }
        });
        return handler;
    }
    
    final static WeakHashMap<Thread, AsyncServer> mServers = new WeakHashMap<Thread, AsyncServer>();

    private boolean addMe() {
        synchronized (mServers) {
            AsyncServer current = mServers.get(mAffinity);
            if (current != null) {
//                Log.e(LOGTAG, "****AsyncServer already running on this thread.****");
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
    private void run(boolean newThread) {
        final SelectorWrapper selector;
        final PriorityQueue<Scheduled> queue;
        boolean reentrant = false;
        synchronized (this) {
            if (mSelector != null) {
                Log.i(LOGTAG, "Reentrant call");
                assert Thread.currentThread() == mAffinity;
                // this is reentrant
                reentrant = true;
                selector = mSelector;
                queue = mQueue;
            }
            else {
                try {
                    selector = mSelector = new SelectorWrapper(SelectorProvider.provider().openSelector());
                    queue = mQueue;
                }
                catch (IOException e) {
                    return;
                }
                if (newThread) {
                    mAffinity = new Thread(mName) {
                        public void run() {
                            AsyncServer.run(AsyncServer.this, selector, queue);
                        }
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
                runLoop(this, selector, queue);
            }
            catch (AsyncSelectorException e) {
                Log.e(LOGTAG, "Selector exception", e);
                try {
                    // StreamUtility.closeQuiety is throwing ArrayStoreException?
                    selector.getSelector().close();
                }
                catch (Exception ex) {
                }
            }
            return;
        }
        
        run(this, selector, queue);
    }
    
    private static void run(final AsyncServer server, final SelectorWrapper selector, final PriorityQueue<Scheduled> queue) {
//        Log.i(LOGTAG, "****AsyncServer is starting.****");
        // at this point, this local queue and selector are owned
        // by this thread.
        // if a stop is called, the instance queue and selector
        // will be replaced and nulled respectively.
        // this will allow the old queue and selector to shut down
        // gracefully, while also allowing a new selector thread
        // to start up while the old one is still shutting down.
        while(true) {
            try {
                runLoop(server, selector, queue);
            }
            catch (AsyncSelectorException e) {
                Log.e(LOGTAG, "Selector exception", e);
                try {
                    // StreamUtility.closeQuiety is throwing ArrayStoreException?
                    selector.getSelector().close();
                }
                catch (Exception ex) {
                }
            }
            // see if we keep looping, this must be in a synchronized block since the queue is accessed.
            synchronized (server) {
                if (selector.isOpen() && (selector.keys().size() > 0 || queue.size() > 0))
                    continue;

                shutdownEverything(selector);
                if (server.mSelector == selector) {
                    server.mQueue = new PriorityQueue<Scheduled>(1, Scheduler.INSTANCE);
                    server.mSelector = null;
                    server.mAffinity = null;
                }
                break;
            }
        }
        synchronized (mServers) {
            mServers.remove(Thread.currentThread());
        }
//        Log.i(LOGTAG, "****AsyncServer has shut down.****");
    }

    private static void shutdownKeys(SelectorWrapper selector) {
        try {
            for (SelectionKey key: selector.keys()) {
                StreamUtility.closeQuietly(key.channel());
                try {
                    key.cancel();
                }
                catch (Exception e) {
                }
            }
        }
        catch (Exception ex) {
        }
    }

    private static void shutdownEverything(SelectorWrapper selector) {
        shutdownKeys(selector);
        // SHUT. DOWN. EVERYTHING.
        try {
            selector.close();
        }
        catch (Exception e) {
        }
    }
    
    private static final long QUEUE_EMPTY = Long.MAX_VALUE;
    private static long lockAndRunQueue(final AsyncServer server, final PriorityQueue<Scheduled> queue) {
        long wait = QUEUE_EMPTY;
        
        // find the first item we can actually run
        while (true) {
            Scheduled run = null;

            synchronized (server) {
                long now = System.currentTimeMillis();

                if (queue.size() > 0) {
                    Scheduled s = queue.remove();
                    if (s.time <= now) {
                        run = s;
                    }
                    else {
                        wait = s.time - now;
                        queue.add(s);
                    }
                }
            }
            
            if (run == null)
                break;
            
            run.runnable.run();
        }

        return wait;
    }

    private static class AsyncSelectorException extends IOException {
        public AsyncSelectorException(Exception e) {
            super(e);
        }
    }

    private static void runLoop(final AsyncServer server, final SelectorWrapper selector, final PriorityQueue<Scheduled> queue) throws AsyncSelectorException {
//        Log.i(LOGTAG, "Keys: " + selector.keys().size());
        boolean needsSelect = true;

        // run the queue to populate the selector with keys
        long wait = lockAndRunQueue(server, queue);
        try {
            synchronized (server) {
                // select now to see if anything is ready immediately. this
                // also clears the canceled key queue.
                int readyNow = selector.selectNow();
                if (readyNow == 0) {
                    // if there is nothing to select now, make sure we don't have an empty key set
                    // which means it would be time to turn this thread off.
                    if (selector.keys().size() == 0 && wait == QUEUE_EMPTY) {
//                    Log.i(LOGTAG, "Shutting down. keys: " + selector.keys().size() + " keepRunning: " + keepRunning);
                        return;
                    }
                }
                else {
                    needsSelect = false;
                }
            }

            if (needsSelect) {
                if (wait == QUEUE_EMPTY) {
                    // wait until woken up
                    selector.select();
                }
                else {
                    // nothing to select immediately but there's something pending so let's block that duration and wait.
                    selector.select(wait);
                }
            }
        }
        catch (Exception e) {
            throw new AsyncSelectorException(e);
        }

        // process whatever keys are ready
        Set<SelectionKey> readyKeys = selector.selectedKeys();
        for (SelectionKey key: readyKeys) {
            try {
                if (key.isAcceptable()) {
                    ServerSocketChannel nextReady = (ServerSocketChannel) key.channel();
                    SocketChannel sc = null;
                    SelectionKey ckey = null;
                    try {
                        sc = nextReady.accept();
                        if (sc == null)
                            continue;
                        sc.configureBlocking(false);
                        ckey = sc.register(selector.getSelector(), SelectionKey.OP_READ);
                        ListenCallback serverHandler = (ListenCallback) key.attachment();
                        AsyncNetworkSocket handler = new AsyncNetworkSocket();
                        handler.attach(sc, (InetSocketAddress)sc.socket().getRemoteSocketAddress());
                        handler.setup(server, ckey);
                        ckey.attach(handler);
                        serverHandler.onAccepted(handler);
                    }
                    catch (IOException e) {
                        StreamUtility.closeQuietly(sc);
                        if (ckey != null)
                            ckey.cancel();
                    }
                }
                else if (key.isReadable()) {
                    AsyncNetworkSocket handler = (AsyncNetworkSocket) key.attachment();
                    int transmitted = handler.onReadable();
                    server.onDataReceived(transmitted);
                }
                else if (key.isWritable()) {
                    AsyncNetworkSocket handler = (AsyncNetworkSocket) key.attachment();
                    handler.onDataWritable();
                }
                else if (key.isConnectable()) {
                    ConnectFuture cancel = (ConnectFuture) key.attachment();
                    SocketChannel sc = (SocketChannel) key.channel();
                    key.interestOps(SelectionKey.OP_READ);
                    AsyncNetworkSocket newHandler;
                    try {
                        sc.finishConnect();
                        newHandler = new AsyncNetworkSocket();
                        newHandler.setup(server, key);
                        newHandler.attach(sc, (InetSocketAddress)sc.socket().getRemoteSocketAddress());
                        key.attach(newHandler);
                    }
                    catch (IOException ex) {
                        key.cancel();
                        StreamUtility.closeQuietly(sc);
                        if (cancel.setComplete(ex))
                            cancel.callback.onConnectCompleted(ex, null);
                        continue;
                    }
                    try {
                        if (cancel.setComplete(newHandler))
                            cancel.callback.onConnectCompleted(null, newHandler);
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    Log.i(LOGTAG, "wtf");
                    throw new RuntimeException("Unknown key state.");
                }
            }
            catch (CancelledKeyException ex) {
            }
        }
        readyKeys.clear();
    }

    public void dump() {
        post(new Runnable() {
            @Override
            public void run() {
                if (mSelector == null) {
                    Log.i(LOGTAG, "Server dump not possible. No selector?");
                    return;
                }
                Log.i(LOGTAG, "Key Count: " + mSelector.keys().size());

                for (SelectionKey key: mSelector.keys()) {
                    Log.i(LOGTAG, "Key: " + key);
                }
            }
        });
    }
    
    public Thread getAffinity() {
        return mAffinity;
    }
    
    public boolean isAffinityThread() {
        return mAffinity == Thread.currentThread();
    }

    public boolean isAffinityThreadOrStopped() {
        Thread affinity = mAffinity;
        return affinity == null || affinity == Thread.currentThread();
    }
}
