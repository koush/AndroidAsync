package com.koushikdutta.async.test;

import com.koushikdutta.async.future.SimpleFuture;

class TriggerFuture extends SimpleFuture<Integer> {
    public void trigger() {
        setComplete(2020);
    }
}