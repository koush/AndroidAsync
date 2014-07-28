/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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


import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.Protocol;

/** A version and dialect of the framed socket protocol. */
interface Variant {

  /** The protocol as selected using NPN or ALPN. */
  Protocol getProtocol();

  /**
   * @param client true if this is the HTTP client's reader, reading frames from a server.
   */
  FrameReader newReader(DataEmitter source, FrameReader.Handler handler, boolean client);

  /**
   * @param client true if this is the HTTP client's writer, writing frames to a server.
   */
  FrameWriter newWriter(BufferedDataSink sink, boolean client);

  int maxFrameSize();
}
