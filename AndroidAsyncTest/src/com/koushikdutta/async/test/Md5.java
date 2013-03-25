package com.koushikdutta.async.test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.koushikdutta.async.ByteBufferList;

public class Md5 {
    private MessageDigest digest;
    public static Md5 createInstance() throws NoSuchAlgorithmException {
        Md5 md5 = new Md5();
        md5.digest = MessageDigest.getInstance("MD5");
        return md5;
    }
    
    private Md5() {
        
    }
    public void update(ByteBufferList bb) {
        for (ByteBuffer b: bb)
            digest.update(b);
    }
    
    public String digest() {
        String hash = new BigInteger(digest.digest()).toString(16);
        return hash;
    }
}
