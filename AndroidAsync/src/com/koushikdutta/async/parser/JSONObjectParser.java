package com.koushikdutta.async.parser;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import org.json.JSONObject;

/**
 * Created by koush on 5/27/13.
 */
public class JSONObjectParser implements AsyncParser<JSONObject> {
    @Override
    public Future<JSONObject> parse(DataEmitter emitter, ParserCallback callback) {
        final SimpleFuture<JSONObject> ret = new SimpleFuture<JSONObject>();

        ret.setParent(new StringParser().parse(emitter, callback).setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String result) {
                if (e != null) {
                    ret.setComplete(e);
                    return;
                }
                try {
                    ret.setComplete(new JSONObject(result));
                }
                catch (Exception ex) {
                    ret.setComplete(ex);
                }
            }
        }));

        return ret;
    }
}
