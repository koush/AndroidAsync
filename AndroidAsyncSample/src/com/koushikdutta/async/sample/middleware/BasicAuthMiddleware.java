package com.koushikdutta.async.sample.middleware;

import android.text.TextUtils;
import android.util.Base64;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.SimpleMiddleware;

import java.util.Hashtable;

/**
 * Created by koush on 2/15/15.
 */
public class BasicAuthMiddleware extends SimpleMiddleware {
    // insert this using
    public static BasicAuthMiddleware add(AsyncHttpClient client) {
        BasicAuthMiddleware ret = new BasicAuthMiddleware();
        client.getMiddleware().add(ret);
        return ret;
    }

    @Override
    public void onRequest(OnRequestData data) {
        super.onRequest(data);
        // do more checking here, since uri may not necessarily be http or have a host, etc.
        String auth = auths.get(data.request.getUri().getHost());
        if (!TextUtils.isEmpty(auth))
            data.request.setHeader("Authorization", auth);
    }

    Hashtable<String, String> auths = new Hashtable<String, String>();
    public void setAuthorizationForHost(String host, String username, String password) {
        String auth = "Basic " + Base64.encodeToString(String.format("%s:%s", username, password).getBytes(), Base64.NO_WRAP);
        auths.put(host, auth);
    }
}
