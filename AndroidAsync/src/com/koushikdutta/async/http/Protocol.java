package com.koushikdutta.async.http;

import java.util.Hashtable;
import java.util.Locale;

/**
 * Protocols that OkHttp implements for <a
 * href="http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04">NPN</a> and
 * <a href="http://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg">ALPN</a>
 * selection.
 * <p/>
 * <h3>Protocol vs Scheme</h3>
 * Despite its name, {@link java.net.URL#getProtocol()} returns the
 * {@linkplain java.net.URI#getScheme() scheme} (http, https, etc.) of the URL, not
 * the protocol (http/1.1, spdy/3.1, etc.). OkHttp uses the word <i>protocol</i>
 * to identify how HTTP messages are framed.
 */
public enum Protocol {
    /**
     * An obsolete plaintext framing that does not use persistent sockets by
     * default.
     */
    HTTP_1_0("http/1.0"),

    /**
     * A plaintext framing that includes persistent connections.
     * <p/>
     * <p>This version of OkHttp implements <a
     * href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a>, and tracks
     * revisions to that spec.
     */
    HTTP_1_1("http/1.1"),

    /**
     * Chromium's binary-framed protocol that includes header compression,
     * multiplexing multiple requests on the same socket, and server-push.
     * HTTP/1.1 semantics are layered on SPDY/3.
     * <p/>
     * <p>This version of OkHttp implements SPDY 3 <a
     * href="http://dev.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3-1">draft
     * 3.1</a>. Future releases of OkHttp may use this identifier for a newer draft
     * of the SPDY spec.
     */
    SPDY_3("spdy/3.1") {
        @Override
        public boolean needsSpdyConnection() {
            return true;
        }
    },

    /**
     * The IETF's binary-framed protocol that includes header compression,
     * multiplexing multiple requests on the same socket, and server-push.
     * HTTP/1.1 semantics are layered on HTTP/2.
     * <p/>
     * <p>This version of OkHttp implements HTTP/2 <a
     * href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-13">draft 12</a>
     * with HPACK <a
     * href="http://tools.ietf.org/html/draft-ietf-httpbis-header-compression-08">draft
     * 6</a>. Future releases of OkHttp may use this identifier for a newer draft
     * of these specs.
     */
    HTTP_2("h2-13") {
        @Override
        public boolean needsSpdyConnection() {
            return true;
        }
    };

    private final String protocol;
    private static final Hashtable<String, Protocol> protocols = new Hashtable<String, Protocol>();

    static {
        protocols.put(HTTP_1_0.toString(), HTTP_1_0);
        protocols.put(HTTP_1_1.toString(), HTTP_1_1);
        protocols.put(SPDY_3.toString(), SPDY_3);
        protocols.put(HTTP_2.toString(), HTTP_2);
    }


    Protocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Returns the protocol identified by {@code protocol}.
     */
    public static Protocol get(String protocol) {
        if (protocol == null)
            return null;
        return protocols.get(protocol.toLowerCase(Locale.US));
    }

    /**
     * Returns the string used to identify this protocol for ALPN and NPN, like
     * "http/1.1", "spdy/3.1" or "h2-13".
     */
    @Override
    public String toString() {
        return protocol;
    }

    public boolean needsSpdyConnection() {
        return false;
    }
}
