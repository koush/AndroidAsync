package com.koushikdutta.async;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Created by koush on 2/13/14.
 */
public class SelectorWrapper {
    private Selector selector;
    boolean maybeSelecting;
    boolean needsWake;

    public SelectorWrapper(Selector selector) {
        this.selector = selector;
    }

    public void select() throws IOException {
        select(0);
    }

    public void select(long timeout) throws IOException {
        try {
            synchronized (this) {
                maybeSelecting = true;
                if (needsWake) {
                    selector.selectNow();
                    needsWake = false;
                    return;
                }
            }
            selector.select(timeout);
        }
        finally {
            maybeSelecting = false;
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

    private boolean wakeupOnceInternal() {
        // if a selection is not in progress,
        // that means the current state is such
        // that the reactor thread may block and select at any moment.
        // let's note that that we need to do a nonblocking select in this case.
        synchronized (this) {
            if (!maybeSelecting) {
                needsWake = true;
                return false;
            }
        }
        selector.wakeup();
        return true;
    }

    public void wakeupOnce() {
        if (!wakeupOnceInternal())
            wakeupOnceInternal();
    }
}
