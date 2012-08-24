package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

public interface DataTransformer extends DataEmitter, DataCallback, ExceptionCallback {
}
