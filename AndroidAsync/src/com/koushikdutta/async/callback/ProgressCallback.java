package com.koushikdutta.async.callback;

import com.koushikdutta.async.http.AsyncHttpResponse;

/**
 * Created by koush on 5/27/13.
 */
public interface ProgressCallback {
    public void onProgress(int progress, int total);
}
