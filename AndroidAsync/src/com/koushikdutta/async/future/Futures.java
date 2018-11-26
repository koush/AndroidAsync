package com.koushikdutta.async.future;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class Futures {
    public static <T> Future<ArrayList<T>> waitAll(final Future<T>... futures) {
        final ArrayList<T> results = new ArrayList<>();
        final SimpleFuture<ArrayList<T>> ret = new SimpleFuture<>();

        FutureCallback<T> cb = new FutureCallback<T>() {
            int count = 0;

            @Override
            public void onCompleted(Exception e, T result) {
                results.add(result);
                count++;
                if (count < futures.length)
                    futures[count].setCallback(this);
                else
                    ret.setComplete(results);
            }
        };

        futures[0].setCallback(cb);

        return ret;
    }


    private static <T, F> void loopUntil(final Iterator<F> values, ThenFutureCallback<T, F> callback, SimpleFuture<T> ret, Exception lastException) {
        while (values.hasNext()) {
            try {
                callback.then(values.next())
                        .success(ret::setComplete)
                        .fail(e -> loopUntil(values, callback, ret, e));
                return;
            } catch (Exception e) {
                lastException = e;
            }
        }

        if (lastException == null)
            ret.setComplete(new Exception("empty list"));
        else
            ret.setComplete(lastException);
    }

    public static <T, F> Future<T> loopUntil(final Iterable<F> values, ThenFutureCallback<T, F> callback) {
        SimpleFuture<T> ret = new SimpleFuture<>();
        loopUntil(values.iterator(), callback, ret, null);
        return ret;
    }

    public static <T, F> Future<T> loopUntil(final F[] values, ThenFutureCallback<T, F> callback) {
        return loopUntil(Arrays.asList(values), callback);
    }
}