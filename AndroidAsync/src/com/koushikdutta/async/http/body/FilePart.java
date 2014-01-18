package com.koushikdutta.async.http.body;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

public class FilePart extends StreamPart {
    File file;
    public FilePart(String name, final File file) {
        super(name, (int)file.length(), createFilenameNameValuePairList(file));

        this.file = file;
    }
    
    private static List<NameValuePair> createFilenameNameValuePairList(File file) {
        List<NameValuePair> filenameNameValuePairList = new ArrayList<NameValuePair>();
        filenameNameValuePairList.add(new BasicNameValuePair("filename", file.getName()));
        return filenameNameValuePairList;
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }
}
