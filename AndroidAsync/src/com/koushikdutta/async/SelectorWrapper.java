package com.koushikdutta.async;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by koush on 2/13/14.
 */
public class SelectorWrapper {
    private Selector selector;
    boolean isWaking;
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
        synchronized (this) {
            // check if another thread is already waiting
            if (isWaking) {
//                System.out.println("race wakeup already progressing");
                return;
            }
            isWaking = true;
        }

        try {
//            System.out.println("performing race wakup");
            // try to wake up 10 times
            for (int i = 0; i < 100; i++) {
                try {
                    if (semaphore.tryAcquire(10, TimeUnit.MILLISECONDS)) {
//                        System.out.println("race wakeup success");
                        return;
                    }
                }
                catch (InterruptedException e) {
                }
                selector.wakeup();
            }
        }
        finally {
            synchronized (this) {
                isWaking = false;
            }
        }
    }
}
