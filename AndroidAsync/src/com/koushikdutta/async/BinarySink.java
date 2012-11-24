package com.koushikdutta.async;

import java.nio.ByteBuffer;

public class BinarySink extends BufferedDataSink {
    public BinarySink(DataSink datasink) {
        super(datasink);
    }
    
    private void writeFull(ByteBuffer b) {
        b.position(0);
        b.limit(b.capacity());
        write(b);
    }

    public void writeInt(int i) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        writeFull(bb);
    }
    
    public void writeByte(byte b) {
        ByteBuffer bb = ByteBuffer.allocate(1);
        bb.put(b);
        writeFull(bb);
    }
    
    public void writeShort(short s) {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.putShort(s);
        writeFull(bb);
    }
    
    public void writeString(String s) {
        writeBytes(s.getBytes());
    }
    
    public void writeBytes(byte[] bytes) {
        writeBytes(bytes, 0, bytes.length);
    }

    public void writeBytes(byte[] bytes, int start, int length) {
        writeInt(length);
        write(ByteBuffer.wrap(bytes, start, length));
    }
}
