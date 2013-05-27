package com.koushikdutta.async.parser;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import org.json.JSONObject;

/**
 * Created by koush on 5/27/13.
 */
public class StringParser implements AsyncParser<String> {
    @Override
    public Future<String> parse(DataEmitter emitter, ParserCallback callback) {
        final SimpleFuture<String> ret = new SimpleFuture<String>();

        ret.setParent(new ByteBufferListParser().parse(emitter, callback).setCallback(new FutureCallback<ByteBufferList>() {
            @Override
            public void onCompleted(Exception e, ByteBufferList result) {
                if (e != null) {
                    ret.setComplete(e);
                    return;
                }
                try {
                    ret.setComplete(result.readString());
                }
                catch (Exception ex) {
                    ret.setComplete(ex);
                }
            }
        }));

        return ret;
    }
}
