package com.koushikdutta.async.future;

public interface DependentFuture<T> extends Future<T>, DependentCancellable {
}
