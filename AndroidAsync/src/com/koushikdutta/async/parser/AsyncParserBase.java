package com.koushikdutta.async.parser;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by koush on 3/6/15.
 */
public abstract class AsyncParserBase<T> implements AsyncParser<T> {
    @Override
    public Type getType() {
        return ((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
