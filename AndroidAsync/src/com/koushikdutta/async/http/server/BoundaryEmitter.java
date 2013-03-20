package com.koushikdutta.async.http.server;

import java.nio.ByteBuffer;

import junit.framework.Assert;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;

public class BoundaryEmitter extends FilteredDataEmitter {
    private byte[] boundary;
    public BoundaryEmitter(String boundary) {
        this.boundary = ("--" + boundary).getBytes();
    }
    
    protected void onBoundaryStart() {
    }
    
    protected void onBoundaryEnd() {
    }
    
    // >= 0 matching
    // -1 matching - (start of boundary end) or \r (boundary start)
    // -2 matching - (end of boundary end)
    // -3 matching \r after boundary
    // -4 matching \n after boundary
    // defunct: -5 matching start - MUST match the start of the first boundary
    int state = 0;
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
//        System.out.println(bb.getString());
//        System.out.println("chunk: " + bb.remaining());
        
//        System.out.println("state: " + state);
        
        // if we were in the middle of a potential match, let's throw that
        // at the beginning of the buffer and process it too.
        if (state > 0) {
            ByteBuffer b = ByteBuffer.wrap(boundary, 0, state).duplicate();
            bb.add(0, b);
            state = 0;
        }
        
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
                else if (state > 0) {
                    // let's try matching again one byte after the start
                    // of last match occurrence
                    i -= state;
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
                        super.onDataAvailable(this, list);
                    }
                    else {
                        // len can be -1 on the first boundary
                        Assert.assertEquals(-2, len);
                    }
//                    System.out.println("bstart");
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
                    super.onDataAvailable(this, list);
//                    System.out.println("bend");
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
//            else if (state == -5) {
//                Assert.assertEquals(i, 0);
//                if (buf[i] == boundary[i]) {
//                    state = 1;
//                }
//                else {
//                    report(new Exception("Invalid multipart/form-data. Expected boundary start: '" + (char)boundary[i] + "'"));
//                    return;
//                }
//            }
            else {
                Assert.fail();
                report(new Exception("Invalid multipart/form-data. Unknown state?"));
            }
        }

        if (last < buf.length) {
//            System.out.println("amount left at boundary: " + (buf.length - last));
//            System.out.println(state);
            int keep = Math.max(state, 0);
            ByteBuffer b = ByteBuffer.wrap(buf, last, buf.length - last - keep);
            ByteBufferList list = new ByteBufferList();
            list.add(b);
            super.onDataAvailable(this, list);
        }
    }
}
