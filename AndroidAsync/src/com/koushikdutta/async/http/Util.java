package com.koushikdutta.async.http;

import junit.framework.Assert;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.ExceptionCallback;
import com.koushikdutta.async.FilteredDataCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.filter.ChunkedInputFilter;
import com.koushikdutta.async.http.filter.GZIPInputFilter;
import com.koushikdutta.async.http.filter.InflaterInputFilter;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.server.UnknownRequestBody;

public class Util {
    public static AsyncHttpRequestBody getBody(DataEmitter emitter, RawHeaders headers) {
        return new UnknownRequestBody(emitter, headers.get("Content-Type"));
    }
    
    public static DataCallback getBodyDecoder(DataCallback callback, RawHeaders headers, final ExceptionCallback reporter) {
        if ("gzip".equals(headers.get("Content-Encoding"))) {
            GZIPInputFilter gunzipper = new GZIPInputFilter();
            gunzipper.setDataCallback(callback);
            gunzipper.setExceptionCallback(reporter);
            callback = gunzipper;
        }        
        else if ("deflate".equals(headers.get("Content-Encoding"))) {
            InflaterInputFilter inflater = new InflaterInputFilter();
            inflater.setExceptionCallback(reporter);
            inflater.setDataCallback(callback);
            callback = inflater;
        }

        int _contentLength;
        try {
            _contentLength = Integer.parseInt(headers.get("Content-Length"));
        }
        catch (Exception ex) {
            _contentLength = -1;
        }
        final int contentLength = _contentLength;
        if (!"chunked".equalsIgnoreCase(headers.get("Transfer-Encoding"))) {
            if (contentLength < 0) {
                reporter.onException(new Exception("not using chunked encoding, and no content-length found."));
            }
            FilteredDataCallback contentLengthWatcher = new FilteredDataCallback() {
                int totalRead = 0;
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    totalRead += bb.remaining();
                    Assert.assertTrue(totalRead <= contentLength);
                    super.onDataAvailable(emitter, bb);
                    if (totalRead == contentLength)
                        reporter.onException(null);
                }
            };
            contentLengthWatcher.setDataCallback(callback);
            callback = contentLengthWatcher;
        }
        else {
            ChunkedInputFilter chunker = new ChunkedInputFilter() {
                @Override
                public void onCompleted(Exception ex) {
                    reporter.onException(ex);
                }
            };
            
            chunker.setExceptionCallback(reporter);
            chunker.setDataCallback(callback);
            callback = chunker;
        }
        return callback;
    }
}
