package com.koushikdutta.async.test;

import android.test.AndroidTestCase;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.FileDataEmitter;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.parser.StringParser;
import com.koushikdutta.async.util.StreamUtility;

import junit.framework.TestCase;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by koush on 5/22/13.
 */
public class FileTests extends AndroidTestCase {
    public static final long TIMEOUT = 1000L;
    public void testFileDataEmitter() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        File f = getContext().getFileStreamPath("test.txt");
        StreamUtility.writeFile(f, "hello world");
        FileDataEmitter fdm = new FileDataEmitter(AsyncServer.getDefault(), f);
        final Md5 md5 = Md5.createInstance();
        Future<String> stringBody = new StringParser().parse(fdm)
        .setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String result) {
                semaphore.release();
            }
        });
        fdm.resume();

        assertTrue("timeout", semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals("hello world", stringBody.get());
    }
}
