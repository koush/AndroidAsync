package com.koushikdutta.async.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.koushikdutta.async.http.libcore.RawHeaders;

public class HeaderMap {
    public static Map<String,String> parse(RawHeaders headers, String header) {
        HashMap<String, String> map = new HashMap<String, String>();
        String value = headers.get(header);
        String[] parts = value.split(";");
        for (String part: parts) {
            String[] pair = part.split("=", 2);
            String key = pair[0].trim();
            String v = null;
            if (pair.length > 1)
                v = pair[1];
            if (v != null && v.endsWith("\"") && v.startsWith("\""))
                v = v.substring(1, v.length() - 1);
            map.put(key, v);
        }
        return Collections.unmodifiableMap(map);
    }
}
