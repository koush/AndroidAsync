package com.koushikdutta.async.http;

import com.koushikdutta.async.callback.DataCallback;

public interface MultipartCallback {
    public DataCallback onPart(Part part);
}
