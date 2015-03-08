package com.koushikdutta.async.parser;

import java.lang.reflect.ParameterizedType;

/**
 * Created by koush on 3/6/15.
 */
public abstract class AsyncParserBase<T> implements AsyncParser<T> {
    @Override
    public Class<T> getType() {
        return (Class<T>)((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
