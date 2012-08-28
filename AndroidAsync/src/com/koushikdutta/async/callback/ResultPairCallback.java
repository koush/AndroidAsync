package com.koushikdutta.async.callback;

public interface ResultPairCallback<T, U> {
    public void onCompleted(Exception e, T result, U result2);
}
