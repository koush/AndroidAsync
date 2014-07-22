package com.koushikdutta.async.http.cache;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by koush on 7/21/14.
 */
class CacheUtil {
    static Set<String> varyFields(RawHeaders headers) {
        HashSet<String> ret = new HashSet<String>();
        String value = headers.get("Vary");
        if (value == null)
            return ret;
        for (String varyField : value.split(",")) {
            ret.add(varyField.trim());
        }
        return ret;
    }

    static boolean isCacheable(RawHeaders requestHeaders, RawHeaders responseHeaders) {
        ResponseHeaders r = new ResponseHeaders(null, responseHeaders);
        return r.isCacheable(new RequestHeaders(null, requestHeaders));
    }

    static boolean isNoCache(RawHeaders headers) {
        return new RequestHeaders(null, headers).isNoCache();
    }

    ResponseSource chooseResponseSource(long nowMillis, RawHeaders request, RawHeaders response) {
        RequestHeaders requestHeaders = new RequestHeaders(null, request);
        ResponseHeaders responseHeaders = new ResponseHeaders(null, response);
        return responseHeaders.chooseResponseSource(nowMillis, requestHeaders);
    }
}
