package com.koushikdutta.async;

public class FilteredDataSink extends BufferedDataSink {
    public FilteredDataSink(DataSink sink) {
        super(sink);
        setMaxBuffer(0);
    }
    
    public ByteBufferList filter(ByteBufferList bb) {
        return bb;
    }

    @Override
    protected void onDataAccepted(ByteBufferList bb) {
        ByteBufferList filtered = filter(bb);
        // filtering may return the same byte buffer, so watch for that.
        if (filtered != bb) {
            bb.recycle();
            filtered.get(bb);
        }
    }
}
