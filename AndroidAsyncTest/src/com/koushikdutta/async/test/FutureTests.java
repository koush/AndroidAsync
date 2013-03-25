package com.koushikdutta.async.test;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ContinuationCallback;
import com.koushikdutta.async.future.Continuation;
import com.koushikdutta.async.future.SimpleFuture;

public class FutureTests extends TestCase {
    private static class IntegerFuture extends SimpleFuture<Integer> {
        private IntegerFuture() {
        }

        public static IntegerFuture create(final int value, final long timeout) {
            final IntegerFuture ret = new IntegerFuture();
            
            new Thread() {
                public void run() {
                    try {
                        Thread.sleep(timeout);
                        ret.setComplete(value);
                    }
                    catch (Exception e) {
                        ret.setComplete(e);
                    }
                };
            }.start();
            
            return ret;
        }
    }
    
    public void testIntegerFuture() throws Exception {
        IntegerFuture i = IntegerFuture.create(10, 500L);
        assertEquals((int)i.get(), 10);
    }
    
    public void testContinuation() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Continuation c = new Continuation(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                semaphore.release();
            }
        });
        
        c.add(new ContinuationCallback() {
            @Override
            public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                Thread.sleep(200);
                next.onCompleted(null);
            }
        });
        
        new Thread() {
            public void run() {
                c.start();
            };
        }.start();
        
        assertTrue(semaphore.tryAcquire(3000, TimeUnit.MILLISECONDS));
    }
    
    public void testFutureChain() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Continuation c = new Continuation(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                semaphore.release();
            }
        });
        
        IntegerFuture i1;
        c.add(i1 = IntegerFuture.create(2, 200));
        
        IntegerFuture i2;
        c.add(i2 = IntegerFuture.create(3, 200));
        
        new Thread() {
            public void run() {
                c.start();
            };
        }.start();

        assertTrue(semaphore.tryAcquire(3000, TimeUnit.MILLISECONDS));
        
        assertEquals((int)i1.get(), 2);
        assertEquals((int)i2.get(), 3);
    }
}
