package com.koushikdutta.async.test;


import androidx.test.runner.AndroidJUnit4;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.FileDataEmitter;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.parser.StringParser;
import com.koushikdutta.async.util.StreamUtility;

import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by koush on 5/22/13.
 */
@RunWith(AndroidJUnit4.class)
public class FileTests {
    public static final long TIMEOUT = 1000L;
    public void testFileDataEmitter() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        File f = getContext().getFileStreamPath("test.txt");
        StreamUtility.writeFile(f, "hello world");
        FileDataEmitter fdm = new FileDataEmitter(AsyncServer.getDefault(), f);
        final Md5 md5 = Md5.createInstance();
        Future<String> stringBody = new StringParser().parse(fdm);
        stringBody
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
