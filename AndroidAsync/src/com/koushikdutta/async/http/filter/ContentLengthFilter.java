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
        ByteBufferList list = bb.get(Math.min(contentLength - totalRead, bb.remaining()));
        totalRead += list.remaining();
        super.onDataAvailable(emitter, list);
        if (totalRead == contentLength)
            report(null);
    }
}
