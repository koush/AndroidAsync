package com.koushikdutta.async.http.body;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class FilePart extends StreamPart {
    File file;
    public FilePart(String name, final File file) {
        super(name, (int)file.length(), new ArrayList<NameValuePair>() {
            {
                add(new BasicNameValuePair("filename", file.getName()));
            }
        });
        /*
         * Fix FilePart.java does not contain Content-Type lead to the server receives the damaged file
         */
        getRawHeaders().set("Content-Type", "application/binary");

        this.file = file;
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }
}
