package com.koushikdutta.async.http;

import java.io.File;
import java.util.Map;

import com.koushikdutta.async.http.libcore.RawHeaders;

public class Part {
    RawHeaders mHeaders;
    Map<String, String> mContentDisposition;
    public Part(RawHeaders headers) {
        mHeaders = headers;
        mContentDisposition = HeaderMap.parse(mHeaders, "Content-Disposition");
    }
    
    public RawHeaders getRawHeaders() {
        return mHeaders;
    }
    
    public String getContentType() {
        return mHeaders.get("Content-Type");
    }
    
    public String getFilename() {
        String file = mContentDisposition.get("filename");
        if (file == null)
            return null;
        return new File(file).getName();
    }

    public boolean isFile() {
        return mContentDisposition.containsKey("filename");
    }
}
