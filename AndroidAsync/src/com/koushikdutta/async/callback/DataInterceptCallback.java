package com.koushikdutta.async.callback;

import java.nio.ByteBuffer;

/**
 *  AsyncHttpServerResponseImpl 에서 sendStream() 사용시에 inputStream 에 대한 조작이 가능하도록 callback 추가.
 *
 * Created by erkas on 2014. 4. 7..
 */
public interface DataInterceptCallback {
    public void onDataAvailable(ByteBuffer buffer, int dataLength);
}
