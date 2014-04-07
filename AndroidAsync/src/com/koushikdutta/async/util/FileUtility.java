package com.koushikdutta.async.util;

import java.io.File;

/**
 * Created by koush on 4/7/14.
 */
public class FileUtility {
    static public boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        deleteDirectory(files[i]);
                    }
                    else {
                        files[i].delete();
                    }
                }
            }
        }
        return (path.delete());
    }
}
