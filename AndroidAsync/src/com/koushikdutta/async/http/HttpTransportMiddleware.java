package com.koushikdutta.async.http;

import android.text.TextUtils;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;

import java.io.IOException;

/**
 * Created by koush on 7/24/14.
 */
public class HttpTransportMiddleware extends SimpleMiddleware {
    @Override
    public boolean exchangeHeaders(final OnExchangeHeaderData data) {
        Protocol p = Protocol.get(data.protocol);
        if (p != null && p != Protocol.HTTP_1_0 && p != Protocol.HTTP_1_1)
            return super.exchangeHeaders(data);

        AsyncHttpRequest request = data.request;
        AsyncHttpRequestBody requestBody = data.request.getBody();

        if (requestBody != null) {
            if (requestBody.length() >= 0) {
                request.getHeaders().set("Content-Length", String.valueOf(requestBody.length()));
                data.response.sink(data.socket);
            }
            else if ("close".equals(request.getHeaders().get("Connection"))) {
                data.response.sink(data.socket);
            }
            else {
                request.getHeaders().set("Transfer-Encoding", "Chunked");
                data.response.sink(new ChunkedOutputFilter(data.socket));
            }
        }

        String rl = request.getRequestLine().toString();
        String rs = request.getHeaders().toPrefixString(rl);
        request.logv("\n" + rs);

        Util.writeAll(data.socket, rs.getBytes(), data.sendHeadersCallback);

        LineEmitter.StringCallback headerCallback = new LineEmitter.StringCallback() {
            Headers mRawHeaders = new Headers();
            String statusLine;

            @Override
            public void onStringAvailable(String s) {
                try {
                    s = s.trim();
                    if (statusLine == null) {
                        statusLine = s;
                    }
                    else if (!TextUtils.isEmpty(s)) {
                        mRawHeaders.addLine(s);
                    }
                    else {
                        String[] parts = statusLine.split(" ", 3);
                        if (parts.length < 2)
                            throw new Exception(new IOException("Not HTTP"));

                        data.response.headers(mRawHeaders);
                        String protocol = parts[0];
                        data.response.protocol(protocol);
                        data.response.code(Integer.parseInt(parts[1]));
                        data.response.message(parts.length == 3 ? parts[2] : "");
                        data.receiveHeadersCallback.onCompleted(null);

                        // socket may get detached after headers (websocket)
                        AsyncSocket socket = data.response.socket();
                        if (socket == null)
                            return;
                        DataEmitter emitter;
                        // HEAD requests must not return any data. They still may
                        // return content length, etc, which will confuse the body decoder
                        if (AsyncHttpHead.METHOD.equalsIgnoreCase(data.request.getMethod())) {
                            emitter = HttpUtil.EndEmitter.create(socket.getServer(), null);
                        }
                        else {
                            emitter = HttpUtil.getBodyDecoder(socket, Protocol.get(protocol), mRawHeaders, false);
                        }
                        data.response.emitter(emitter);
                    }
                }
                catch (Exception ex) {
                    data.receiveHeadersCallback.onCompleted(ex);
                }
            }
        };

        LineEmitter liner = new LineEmitter();
        data.socket.setDataCallback(liner);
        liner.setLineCallback(headerCallback);
        return true;
    }

    @Override
    public void onRequestSent(OnRequestSentData data) {
        Protocol p = Protocol.get(data.protocol);
        if (p != null && p != Protocol.HTTP_1_0 && p != Protocol.HTTP_1_1)
            return;

        if (data.response.sink() instanceof ChunkedOutputFilter)
            data.response.sink().end();
    }
}
