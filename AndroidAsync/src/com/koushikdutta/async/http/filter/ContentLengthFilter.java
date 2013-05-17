package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;

public class ContentLengthFilter extends FilteredDataEmitter {
    public ContentLengthFilter(int contentLength) {
        this.contentLength = contentLength;
    }
    
    int contentLength;
    int totalRead;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        assert totalRead < contentLength;
        int remaining = bb.remaining();
        int toRead = Math.min(contentLength - totalRead, remaining);

        ByteBufferList list;
        if (toRead == remaining) {
            list = bb;
        }
        else {
            list = bb.get(toRead);
        }

        totalRead += list.remaining();
        super.onDataAvailable(emitter, list);
        if (totalRead == contentLength)
            report(null);
    }
}
