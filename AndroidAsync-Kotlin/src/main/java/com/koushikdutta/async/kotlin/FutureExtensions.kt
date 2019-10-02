package com.koushikdutta.async.kotlin

import com.koushikdutta.async.future.Future
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> Future<T>.await(): T {
    return suspendCoroutine {
        this.setCallback { e, result ->
            if (e != null)
                it.resumeWithException(e)
            else
                it.resume(result)
        }
    }
}