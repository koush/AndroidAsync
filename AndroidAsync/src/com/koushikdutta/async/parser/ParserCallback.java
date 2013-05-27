package com.koushikdutta.async.parser;

import com.koushikdutta.async.http.AsyncHttpResponse;

/**
 * Created by koush on 5/27/13.
 */
public interface ParserCallback {
    public void onProgress(int bytesParsed);
}
