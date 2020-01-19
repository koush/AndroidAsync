package com.koushikdutta.async;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by koush on 2/13/14.
 */
class SelectorWrapper implements Closeable {
    private Selector selector;
    public AtomicBoolean isWaking = new AtomicBoolean(false);
    Semaphore semaphore = new Semaphore(0);
    public Selector getSelector() {
        return selector;
    }

    public SelectorWrapper(Selector selector) {
        this.selector = selector;
    }

    public int selectNow() throws IOException {
        return selector.selectNow();
    }

    public void select() throws IOException {
        select(0);
    }

    public void select(long timeout) throws IOException {
        try {
            semaphore.drainPermits();
            selector.select(timeout);
        }
        finally {
            semaphore.release(Integer.MAX_VALUE);
        }
    }

    public Set<SelectionKey> keys() {
        return selector.keys();
    }

    public Set<SelectionKey> selectedKeys() {
        return selector.selectedKeys();
    }

    @Override
    public void close() throws IOException {
        selector.close();
    }

    public boolean isOpen() {
        return selector.isOpen();
    }

    public void wakeupOnce() {
        // see if it is selecting, ie, can't acquire a permit
        boolean selecting = !semaphore.tryAcquire();
        selector.wakeup();
        // if it was selecting, then the wakeup definitely worked.
        if (selecting)
            return;

        // now, we NEED to wait for the select to start to forcibly wake it.
        if (isWaking.getAndSet(true)) {
            selector.wakeup();
            return;
        }

        try {
            waitForSelect();
            selector.wakeup();
        } finally {
            isWaking.set(false);
        }
    }

    public boolean waitForSelect() {
        // try to wake up 10 times
        for (int i = 0; i < 100; i++) {
            try {
                if (semaphore.tryAcquire(10, TimeUnit.MILLISECONDS)) {
                    // successfully acquiring means the selector is NOT selecting, since select
                    // will drain all permits.
                    continue;
                }
            } catch (InterruptedException e) {
                // an InterruptedException means the acquire failed a select is in progress,
                // since it holds all permits
                return true;
            }
        }
        return false;
    }
}
