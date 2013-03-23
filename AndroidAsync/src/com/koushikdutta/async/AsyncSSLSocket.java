package com.koushikdutta.async;

import java.security.cert.X509Certificate;

public interface AsyncSSLSocket extends AsyncSocket {
    public X509Certificate[] getPeerCertificates();
}
