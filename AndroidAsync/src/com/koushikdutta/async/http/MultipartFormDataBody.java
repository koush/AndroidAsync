package com.koushikdutta.async.http;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.LineEmitter.StringCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.server.AsyncHttpRequestBodyBase;
import com.koushikdutta.async.http.server.BoundaryEmitter;

public class MultipartFormDataBody extends AsyncHttpRequestBodyBase {
    public static final String CONTENT_TYPE = "multipart/form-data";
    BoundaryEmitter boundaryEmitter;
    String boundary;
    public MultipartFormDataBody(String contentType, String[] values) {
        super(contentType);
        for (String value: values) {
            String[] splits = value.split("=");
            if (splits.length != 2)
                continue;
            if (!"boundary".equals(splits[0]))
                continue;
            boundary = splits[1];
            boundaryEmitter = new BoundaryEmitter(boundary) {
                @Override
                protected void onBoundaryStart() {
                    final RawHeaders headers = new RawHeaders();
                    new LineEmitter(boundaryEmitter).setLineCallback(new StringCallback() {
                        @Override
                        public void onStringAvailable(String s) {
                            if (!"\r".equals(s)){
                                headers.addLine(s);
                            }
                            else {
                                Part part = new Part(headers);
                                DataCallback callback = onPart(part);
                                if (callback == null)
                                    callback = new DataCallback() {
                                        int total;
                                        @Override
                                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                            total += bb.remaining();
//                                            System.out.println(total);
//                                            System.out.println(bb.peekString());
                                            bb.clear();
                                        }
                                    };
                                boundaryEmitter.setDataCallback(callback);
                            }
                        }
                    });
                }
                @Override
                protected void onBoundaryEnd() {
//                    System.out.println("boundary end");
                }
            };
            return;
        }
        report(new Exception ("No boundary found for multipart/form-data"));
    }
    
    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        boundaryEmitter.onDataAvailable(emitter, bb);
    }
    
    MultipartCallback mCallback;
    public void setMultipartCallback(MultipartCallback callback) {
        mCallback = callback;
    }
    
    public MultipartCallback getMultipartCallback() {
        return mCallback;
    }
    
    private DataCallback onPart(Part part) {
//        System.out.println("here");
//        System.out.println(headers.toHeaderString());
        if (mCallback == null)
            return null;
        return mCallback.onPart(part);
    }
}
