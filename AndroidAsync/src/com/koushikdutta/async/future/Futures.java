package com.koushikdutta.async.future;

import java.util.ArrayList;

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
}
