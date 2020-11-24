package com.koushikdutta.async;

import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.callback.SocketCreateCallback;
import com.koushikdutta.async.callback.ValueFunction;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.util.StreamUtility;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

        // run it in a blocking AsyncSemaphore or a Handler, whichever gets to it first.
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

    private static ExecutorService synchronousWorkers = newSynchronousWorkers("AsyncServer-worker-");
    private static void wakeup(final SelectorWrapper selector) {
        synchronousWorkers.execute(() -> {
            try {
                selector.wakeupOnce();
            }
            catch (Exception e) {
            }
        });
    }

    boolean killed;
    public void kill() {
        synchronized (this) {
            killed = true;
        }
        stop(false);
    }

    int postCounter = 0;
    public Cancellable postDelayed(Runnable runnable, long delay) {
        Scheduled s;
        synchronized (this) {
            if (killed)
                return SimpleCancellable.CANCELLED;

            // Calculate when to run this queue item:
            // If there is a delay (non-zero), add it to the current time
            // When delay is zero, ensure that this follows all other
            // zero-delay queue items. This is done by setting the
            // "time" to the queue size. This will make sure it is before
            // all time-delayed queue items (for all real world scenarios)
            // as it will always be less than the current time and also remain
            // behind all other immediately run queue items.
            long time;
            if (delay > 0)
                time = SystemClock.elapsedRealtime() + delay;
            else if (delay == 0)
                time = postCounter++;
            else if (mQueue.size() > 0)
                time = Math.min(0, mQueue.peek().time - 1);
            else
                time = 0;
            mQueue.add(s = new Scheduled(this, runnable, time));
            // start the server up if necessary
            if (mSelector == null)
                run();
            if (!isAffinityThread()) {
                wakeup(mSelector);
            }
        }
        return s;
    }

    public Cancellable postImmediate(Runnable runnable) {
        if (Thread.currentThread() == getAffinity()) {
            runnable.run();
            return null;
        }
        return postDelayed(runnable, -1);
    }

    public Cancellable post(Runnable runnable) {
        return postDelayed(runnable, 0);
    }

    public Cancellable post(final CompletedCallback callback, final Exception e) {
        return post(() -> callback.onCompleted(e));
    }

    public void run(final Runnable runnable) {
        if (Thread.currentThread() == mAffinity) {
            post(runnable);
            lockAndRunQueue(this, mQueue);
            return;
        }

        final Semaphore semaphore;
        synchronized (this) {
            if (killed)
                return;
            semaphore = new Semaphore(0);
            post(() -> {
                runnable.run();
                semaphore.release();
            });
        }
        try {
            semaphore.acquire();
        }
        catch (InterruptedException e) {
            Log.e(LOGTAG, "run", e);
        }
    }

    private static class Scheduled implements Cancellable, Runnable {
        // this constructor is only called when the async execution should not be preserved
        // ie... AsyncServer.stop.
        public Scheduled(AsyncServer server, Runnable runnable, long time) {
            this.server = server;
            this.runnable = runnable;
            this.time = time;
        }
        public AsyncServer server;
        public Runnable runnable;
        public long time;

        @Override
        public void run() {
            this.runnable.run();
        }

        @Override
        public boolean isDone() {
            synchronized (server) {
                return !cancelled && !server.mQueue.contains(this);
            }
        }

        boolean cancelled;
        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean cancel() {
            synchronized (server) {
                return cancelled = server.mQueue.remove(this);
            }
        }
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
        stop(false);
    }

    public void stop(boolean wait) {
//        Log.i(LOGTAG, "****AsyncServer is shutting down.****");
        final SelectorWrapper currentSelector;
        final Semaphore semaphore;
        final boolean isAffinityThread;
        synchronized (this) {
            isAffinityThread = isAffinityThread();
            currentSelector = mSelector;
            if (currentSelector == null)
                return;
            semaphore = new Semaphore(0);

            // post a shutdown and wait
            mQueue.add(new Scheduled(this, new Runnable() {
                @Override
                public void run() {
                    shutdownEverything(currentSelector);
                    semaphore.release();
                }
            }, 0));
            synchronousWorkers.execute(() -> {
                try {
                    currentSelector.wakeupOnce();
                }
                catch (Exception e) {
                }
            });

            // force any existing connections to die
            shutdownKeys(currentSelector);

            mQueue = new PriorityQueue<>(1, Scheduler.INSTANCE);
            mSelector = null;
            mAffinity = null;
        }
        try {
            if (!isAffinityThread && wait)
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
        final ObjectHolder<AsyncServerSocket> holder = new ObjectHolder<>();
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
                    Log.e(LOGTAG, "wtf", e);
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

    public Cancellable connectResolvedInetSocketAddress(final InetSocketAddress address, final ConnectCallback callback) {
        return connectResolvedInetSocketAddress(address, callback, null);
    }

    public ConnectFuture connectResolvedInetSocketAddress(final InetSocketAddress address, final ConnectCallback callback, final SocketCreateCallback createCallback) {
        final ConnectFuture cancel = new ConnectFuture();

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
                    if (createCallback != null)
                        createCallback.onSocketCreated(socket.socket().getLocalPort());
                    socket.connect(address);
                }
                catch (Throwable e) {
                    if (ckey != null)
                        ckey.cancel();
                    StreamUtility.closeQuietly(socket);
                    cancel.setComplete(new RuntimeException(e));
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

                ret.setComplete((ConnectFuture)connectResolvedInetSocketAddress(new InetSocketAddress(result, remote.getPort()), callback));
            }
        });
        return ret;
    }

    public Cancellable connectSocket(final String host, final int port, final ConnectCallback callback) {
        return connectSocket(InetSocketAddress.createUnresolved(host, port), callback);
    }

    private static ExecutorService newSynchronousWorkers(String prefix) {
        ThreadFactory tf = new NamedThreadFactory(prefix);
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(0, 4, 10L,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), tf);
        return tpe;
    }

    private static final Comparator<InetAddress> ipSorter = new Comparator<InetAddress>() {
        @Override
        public int compare(InetAddress lhs, InetAddress rhs) {
            if (lhs instanceof Inet4Address && rhs instanceof Inet4Address)
                return 0;
            if (lhs instanceof Inet6Address && rhs instanceof Inet6Address)
                return 0;
            if (lhs instanceof Inet4Address && rhs instanceof Inet6Address)
                return -1;
            return 1;
        }
    };

    private static ExecutorService synchronousResolverWorkers = newSynchronousWorkers("AsyncServer-resolver-");
    public Future<InetAddress[]> getAllByName(final String host) {
        final SimpleFuture<InetAddress[]> ret = new SimpleFuture<InetAddress[]>();
        synchronousResolverWorkers.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final InetAddress[] result = InetAddress.getAllByName(host);
                    Arrays.sort(result, ipSorter);
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
        return ret;
    }

    public Future<InetAddress> getByName(String host) {
        return getAllByName(host).thenConvert(addresses -> addresses[0]);
    }

    private void handleSocket(final AsyncNetworkSocket handler) throws ClosedChannelException {
        final ChannelWrapper sc = handler.getChannel();
        SelectionKey ckey = sc.register(mSelector.getSelector());
        ckey.attach(handler);
        handler.setup(this, ckey);
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

    public AsyncDatagramSocket openDatagram() {
        return openDatagram(null, 0, false);
    }

    public Cancellable createDatagram(String address, int port, boolean reuseAddress, FutureCallback<AsyncDatagramSocket> callback) {
        return createDatagram(() -> InetAddress.getByName(address), port, reuseAddress, callback);
    }

    public Cancellable createDatagram(InetAddress address, int port, boolean reuseAddress, FutureCallback<AsyncDatagramSocket> callback) {
        return createDatagram(() -> address, port, reuseAddress, callback);
    }

    private Cancellable createDatagram(ValueFunction<InetAddress> inetAddressValueFunction, final int port, final boolean reuseAddress, FutureCallback<AsyncDatagramSocket> callback) {
        SimpleFuture<AsyncDatagramSocket> ret = new SimpleFuture<>();
        ret.setCallback(callback);
        post(() -> {
            DatagramChannel socket = null;
            try {
                socket = DatagramChannel.open();

                final AsyncDatagramSocket handler = new AsyncDatagramSocket();
                handler.attach(socket);

                InetSocketAddress address;
                if (inetAddressValueFunction == null)
                    address = new InetSocketAddress(port);
                else
                    address = new InetSocketAddress(inetAddressValueFunction.getValue(), port);

                if (reuseAddress)
                    socket.socket().setReuseAddress(reuseAddress);
                socket.socket().bind(address);
                handleSocket(handler);
                if (!ret.setComplete(handler))
                    socket.close();
            }
            catch (Exception e) {
                StreamUtility.closeQuietly(socket);
                ret.setComplete(e);
            }
        });

        return ret;
    }

    public AsyncDatagramSocket openDatagram(final InetAddress host, final int port, final boolean reuseAddress) {
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
        // ugh.. this should really be post to make it nonblocking...
        // but i want datagrams to be immediately writable.
        // they're not really used anyways.
        Runnable runnable = () -> {
            final DatagramChannel socket;
            try {
                socket = DatagramChannel.open();
            }
            catch (Exception e) {
                return;
            }
            try {
                handler.attach(socket);

                InetSocketAddress address;
                if (host == null)
                    address = new InetSocketAddress(port);
                else
                    address = new InetSocketAddress(host, port);

                if (reuseAddress)
                    socket.socket().setReuseAddress(reuseAddress);
                socket.socket().bind(address);
                handleSocket(handler);
            }
            catch (IOException e) {
                Log.e(LOGTAG, "Datagram error", e);
                StreamUtility.closeQuietly(socket);
            }
        };

        if (getAffinity() != Thread.currentThread()) {
            run(runnable);
            return handler;
        }

        runnable.run();
        return handler;
    }

    public AsyncDatagramSocket connectDatagram(final SocketAddress remote) throws IOException {
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
        final DatagramChannel socket = DatagramChannel.open();
        handler.attach(socket);
        // ugh.. this should really be post to make it nonblocking...
        // but i want datagrams to be immediately writable.
        // they're not really used anyways.
        Runnable runnable = () -> {
            try {
                handleSocket(handler);
                socket.connect(remote);
            }
            catch (IOException e) {
                StreamUtility.closeQuietly(socket);
            }
        };

        if (getAffinity() != Thread.currentThread()) {
            run(runnable);
            return handler;
        }

        runnable.run();
        return handler;
    }

    final private static ThreadLocal<AsyncServer> threadServer = new ThreadLocal<>();

    public static AsyncServer getCurrentThreadServer() {
        return threadServer.get();
    }

    Thread mAffinity;
    private void run() {
        final SelectorWrapper selector;
        final PriorityQueue<Scheduled> queue;
        synchronized (this) {
            if (mSelector == null) {
                try {
                    selector = mSelector = new SelectorWrapper(SelectorProvider.provider().openSelector());
                    queue = mQueue;
                }
                catch (IOException e) {
                    throw new RuntimeException("unable to create selector?", e);
                }

                mAffinity = new Thread(mName) {
                    public void run() {
                        try {
                            threadServer.set(AsyncServer.this);
                            AsyncServer.run(AsyncServer.this, selector, queue);
                        }
                        finally {
                            threadServer.remove();
                        }
                    }
                };

                mAffinity.start();
                // kicked off the new thread, let's bail.
                return;
            }

            // this is a reentrant call
            selector = mSelector;
            queue = mQueue;

            // fall through to outside of the synchronization scope
            // to allow the thread to run without locking.
        }

        try {
            runLoop(this, selector, queue);
        }
        catch (AsyncSelectorException e) {
            Log.i(LOGTAG, "Selector closed", e);
            try {
                // StreamUtility.closeQuiety is throwing ArrayStoreException?
                selector.getSelector().close();
            }
            catch (Exception ex) {
            }
        }
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
                if (!(e.getCause() instanceof ClosedSelectorException))
                    Log.i(LOGTAG, "Selector exception, shutting down", e);
                StreamUtility.closeQuietly(selector);
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
        StreamUtility.closeQuietly(selector);
    }

    private static final long QUEUE_EMPTY = Long.MAX_VALUE;
    private static long lockAndRunQueue(final AsyncServer server, final PriorityQueue<Scheduled> queue) {
        long wait = QUEUE_EMPTY;

        // find the first item we can actually run
        while (true) {
            Scheduled run = null;

            synchronized (server) {
                long now = SystemClock.elapsedRealtime();

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

            run.run();
        }

        server.postCounter = 0;
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
                    if (cancel.setComplete(newHandler))
                        cancel.callback.onConnectCompleted(null, newHandler);
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

    private static class NamedThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
