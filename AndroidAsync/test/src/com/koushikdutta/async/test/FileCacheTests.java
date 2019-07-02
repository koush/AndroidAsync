package com.koushikdutta.async.test;

import androidx.test.runner.AndroidJUnit4;

import com.koushikdutta.async.util.FileCache;
import com.koushikdutta.async.util.StreamUtility;

import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static androidx.test.InstrumentationRegistry.getContext;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by koush on 4/13/14.
 */
@RunWith(AndroidJUnit4.class)
public class FileCacheTests {
    protected void setUp() throws Exception {
        File dir = new File(getContext().getCacheDir(), "filecache");
        File[] files = dir.listFiles();
        if (files == null)
            return;
        for (File f: files)
            f.delete();
    }

    public void testSimple() throws Exception {
        setUp();

        FileCache cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 100000, false);
        cache.setBlockSize(1);

        File temp = cache.getTempFile();
        StreamUtility.writeFile(temp, "hello");

        cache.commitTempFiles("test", temp);

        String value = StreamUtility.readToEnd(cache.get("test"));
        assertEquals(value, "hello");
    }

    public void testEviction() throws Exception {
        setUp();

        FileCache cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 25, false);
        cache.setBlockSize(1);

        for (int i = 0; i < 10; i++) {
            File temp = cache.getTempFile();
            StreamUtility.writeFile(temp, "hello");
            cache.commitTempFiles("test" + i, temp);
            String value = StreamUtility.readToEnd(cache.get("test" + i));
            assertEquals(value, "hello");
        }

        assertEquals(cache.size(), 25);
        File dir = new File(getContext().getCacheDir(), "filecache");
        File[] files = dir.listFiles();
        assertEquals(files.length, 5);

        for (int i = 5; i < 10; i++) {
            assertTrue(cache.exists("test" + i));
        }
    }

    public void testMultipleParts() throws Exception {
        setUp();

        FileCache cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 100000, false);
        cache.setBlockSize(1);
        File[] temps = new File[10];
        for (int i = 0; i < temps.length; i++) {
            File temp = temps[i] = cache.getTempFile();
            StreamUtility.writeFile(temp, "hello" + i);
        }
        cache.commitTempFiles("test", temps);

        assertEquals(cache.size(), temps.length * 6);
        File dir = new File(getContext().getCacheDir(), "filecache");
        File[] files = dir.listFiles();
        assertEquals(files.length, temps.length);

        for (int i = 5; i < 10; i++) {
            assertTrue(cache.exists("test", i));
        }
    }

    public void testMultipartEviction() throws Exception {
        setUp();

        FileCache cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 12, false);
        cache.setBlockSize(1);
        File[] temps = new File[10];
        for (int i = 0; i < temps.length; i++) {
            File temp = temps[i] = cache.getTempFile();
            StreamUtility.writeFile(temp, "hello" + i);
        }
        cache.commitTempFiles("test", temps);

        assertEquals(cache.size(), 12);
        File dir = new File(getContext().getCacheDir(), "filecache");
        File[] files = dir.listFiles();
        assertEquals(files.length, 2);

        for (int i = 8; i < 10; i++) {
            assertTrue(cache.exists("test", i));
        }

        try {
            FileInputStream[] fins = cache.get("test", temps.length);
            fail();
        }
        catch (IOException e) {
        }
    }


    public void testMultipartEvictionAgain() throws Exception {
        setUp();

        FileCache cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 72, false);
        cache.setBlockSize(1);
        File[] temps = new File[10];
        for (int i = 0; i < temps.length; i++) {
            File temp = temps[i] = cache.getTempFile();
            StreamUtility.writeFile(temp, "hello" + i);
        }
        cache.commitTempFiles("test", temps);

        assertEquals(cache.size(), 60);
        File dir = new File(getContext().getCacheDir(), "filecache");
        File[] files = dir.listFiles();
        assertEquals(files.length, 10);

        for (int i = 0; i < temps.length; i++) {
            assertTrue(cache.exists("test", i));
        }

        FileInputStream[] fins = cache.get("test", temps.length);
        StreamUtility.closeQuietly(fins);

        temps = new File[10];
        for (int i = 0; i < temps.length; i++) {
            File temp = temps[i] = cache.getTempFile();
            StreamUtility.writeFile(temp, "hello" + i);
        }
        cache.commitTempFiles("test2", temps);

        assertEquals(cache.size(), 72);

        fins = cache.get("test2", temps.length);
        StreamUtility.closeQuietly(fins);

        try {
            fins = cache.get("test", temps.length);
            fail();
        }
        catch (IOException e) {
        }
    }

    public void testReinit() throws Exception {
        setUp();

        FileCache cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 10, false);
        cache.setBlockSize(1);
        File temp = cache.getTempFile();
        StreamUtility.writeFile(temp, "hello");
        cache.commitTempFiles("test", temp);

        temp = cache.getTempFile();
        StreamUtility.writeFile(temp, "hello");
        cache.commitTempFiles("test2", temp);

        assertEquals(cache.size(), 10);

        cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 10, false);
        cache.setBlockSize(1);

        String value = StreamUtility.readToEnd(cache.get("test"));
        assertEquals(value, "hello");

        value = StreamUtility.readToEnd(cache.get("test2"));
        assertEquals(value, "hello");
    }

    public void testCacheOrder() throws Exception {
        setUp();

        FileCache cache = new FileCache(new File(getContext().getCacheDir(), "filecache"), 10, false);
        cache.setBlockSize(1);
        File temp = cache.getTempFile();
        StreamUtility.writeFile(temp, "hello");
        cache.commitTempFiles("test", temp);

        temp = cache.getTempFile();
        StreamUtility.writeFile(temp, "hello");
        cache.commitTempFiles("test2", temp);

        assertEquals(cache.size(), 10);

        String value = StreamUtility.readToEnd(cache.get("test"));
        assertEquals(value, "hello");

        // should push test2 off
        temp = cache.getTempFile();
        StreamUtility.writeFile(temp, "hello");
        cache.commitTempFiles("test3", temp);

        assertTrue(cache.exists("test"));
        assertFalse(cache.exists("test2"));
        assertTrue(cache.exists("test3"));
    }
}
