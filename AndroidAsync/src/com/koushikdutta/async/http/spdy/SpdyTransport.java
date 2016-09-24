/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License";
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.koushikdutta.async.http.spdy;


import com.koushikdutta.async.http.Protocol;

import java.util.List;
import java.util.Locale;


final class SpdyTransport {
  /** See http://www.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3-1#TOC-3.2.1-Request. */
  private static final List<String> SPDY_3_PROHIBITED_HEADERS = Util.immutableList(
  "connection",
  "host",
  "keep-alive",
  "proxy-connection",
  "transfer-encoding");

  /** See http://tools.ietf.org/html/draft-ietf-httpbis-http2-09#section-8.1.3. */
  private static final List<String> HTTP_2_PROHIBITED_HEADERS = Util.immutableList(
      "connection",
      "host",
      "keep-alive",
      "proxy-connection",
      "te",
      "transfer-encoding",
      "encoding",
      "upgrade");

  /** When true, this header should not be emitted or consumed. */
  static boolean isProhibitedHeader(Protocol protocol, String name) {
    if (protocol == Protocol.SPDY_3) {
      return SPDY_3_PROHIBITED_HEADERS.contains(name.toLowerCase(Locale.US));
    } else if (protocol == Protocol.HTTP_2) {
      return HTTP_2_PROHIBITED_HEADERS.contains(name.toLowerCase(Locale.US));
    } else {
      throw new AssertionError(protocol);
    }
  }
}
