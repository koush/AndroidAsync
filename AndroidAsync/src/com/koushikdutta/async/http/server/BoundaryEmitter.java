package com.koushikdutta.async.http.server;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataCallback;

public class BoundaryEmitter extends FilteredDataCallback {
    private int mNearMatch;
    private byte[] boundary;
    public BoundaryEmitter(String boundary) {
        this.boundary = ("--" + boundary).getBytes();
        mNearMatch = this.boundary.length - 2;
    }
    
    protected void onBoundaryStart() {
    }
    
    protected void onBoundaryEnd() {
    }
    
    private static int matches(byte[] a1, int o1, byte[] a2, int o2, int count) {
        Assert.assertTrue(count <= a1.length - o1);
        Assert.assertTrue(count <= a2.length - o2);
        for (int i = 0; i < count; i++, o1++, o2++) {
            if (a1[o1] != a2[o2]) {
                System.out.println("match fail at " + i);
                return i;
            }
        }
        return count;
    }
    
    // >= 0 matching
    // -1 matching - (start of boundary end) or \r (boundary start)
    // -2 matching - (end of boundary end)
    // -3 matching \r after boundary
    // -4 matching \n after boundary
    int state = 0;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        System.out.println(bb.getString());
        int last = 0;
        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        for (int i = 0; i < buf.length; i++) {
            if (state >= 0) {
                if (buf[i] == boundary[state]) {
                    state++;
                    if (state == boundary.length)
                        state = -1;
                }
                else {
                    state = 0;
                }
            }
            else if (state == -1) {
                if (buf[i] == '\r') {
                    state = -4;
                    int len = i - last - boundary.length - 2;
                    if (len >= 0) {
                        ByteBuffer b = ByteBuffer.wrap(buf, last, len);
                        ByteBufferList list = new ByteBufferList();
                        list.add(b);
                        super.onDataAvailable(emitter, list);
                    }
                    else {
                        // len can be -1 on the first boundary
                        Assert.assertEquals(-2, len);
                    }
                    onBoundaryStart();
                }
                else if (buf[i] == '-') {
                    state = -2;
                }
                else {
                    report(new Exception("Invalid multipart/form-data. Expected \r or -"));
                    return;
                }
            }
            else if (state == -2) {
                if (buf[i] == '-') {
                    state = -3;
                }
                else {
                    report(new Exception("Invalid multipart/form-data. Expected -"));
                    return;
                }
            }
            else if (state == -3) {
                if (buf[i] == '\r') {
                    state = -4;
                    ByteBuffer b = ByteBuffer.wrap(buf, last, i - last - boundary.length - 4);
                    ByteBufferList list = new ByteBufferList();
                    list.add(b);
                    super.onDataAvailable(emitter, list);
                    onBoundaryEnd();
                }
                else {
                    report(new Exception("Invalid multipart/form-data. Expected \r"));
                    return;
                }
            }
            else if (state == -4) {
                if (buf[i] == '\n') {
                    last = i + 1;
                    state = 0;
                }
                else {
                    report(new Exception("Invalid multipart/form-data. Expected \n"));
                }
            }
            else {
                Assert.fail();
                report(new Exception("Invalid multipart/form-data. Unknown state?"));
            }
        }

        if (last < buf.length) {
            ByteBuffer b = ByteBuffer.wrap(buf, last, buf.length);
            ByteBufferList list = new ByteBufferList();
            list.add(b);
            super.onDataAvailable(emitter, list);
        }
    }
}
