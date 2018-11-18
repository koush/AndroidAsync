package com.koushikdutta.async;

import java.security.PrivateKey;
import java.security.cert.Certificate;

public interface AsyncSSLServerSocket extends AsyncServerSocket {
    PrivateKey getPrivateKey();
    Certificate getCertificate();
}
