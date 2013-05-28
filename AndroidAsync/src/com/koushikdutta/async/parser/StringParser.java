package com.koushikdutta.async.parser;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.TransformFuture;

/**
 * Created by koush on 5/27/13.
 */
public class StringParser implements AsyncParser<String> {
    @Override
    public Future<String> parse(DataEmitter emitter, ParserCallback callback) {
        return new TransformFuture<String, ByteBufferList>() {
            @Override
            protected void transform(ByteBufferList result) throws Exception {
                setComplete(result.readString());
            }
        }
        .from(new ByteBufferListParser().parse(emitter, callback));
    }
}
