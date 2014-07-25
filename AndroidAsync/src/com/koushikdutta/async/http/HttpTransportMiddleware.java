package com.koushikdutta.async.http;

import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;

/**
 * Created by koush on 7/24/14.
 */
public class HttpTransportMiddleware extends SimpleMiddleware {

    @Override
    public boolean sendHeaders(final SendHeaderData data) {
        Protocol p = Protocol.get(data.protocol);
        if (p != null && p != Protocol.HTTP_1_0 && p != Protocol.HTTP_1_1)
            return super.sendHeaders(data);

        AsyncHttpRequest request = data.request;
        AsyncHttpRequestBody requestBody = data.request.getBody();

        if (requestBody != null) {
            if (request.getHeaders().get("Content-Type") == null)
                request.getHeaders().set("Content-Type", requestBody.getContentType());
            if (requestBody.length() >= 0) {
                request.getHeaders().set("Content-Length", String.valueOf(requestBody.length()));
                data.response.sink(data.socket);
            } else {
                request.getHeaders().set("Transfer-Encoding", "Chunked");
                data.response.sink(new ChunkedOutputFilter(data.socket));
            }
        }

        String rl = request.getRequestLine().toString();
        String rs = request.getHeaders().toPrefixString(rl);
        request.logv("\n" + rs);

        Util.writeAll(data.socket, rs.getBytes(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                data.sendHeadersCallback.onCompleted(ex);
            }
        });

//        LineEmitter.StringCallback headerCallback = new LineEmitter.StringCallback() {
//            Headers mRawHeaders = new Headers();
//            String statusLine;
//
//            @Override
//            public void onStringAvailable(String s) {
//                try {
//                    if (statusLine == null) {
//                        statusLine = s;
//                    }
//                    else if (!"\r".equals(s)) {
//                        mRawHeaders.addLine(s);
//                    }
//                    else {
//                        String[] parts = statusLine.split(" ", 3);
//                        if (parts.length != 3)
//                            throw new Exception(new IOException("Not HTTP"));
//
//                        data.response.headers(mRawHeaders);
//                        data.response.protocol(parts[0]);
//                        data.response.code(Integer.parseInt(parts[1]));
//                        data.response.message(parts[2]);
//                        data.sendHeadersCallback.onCompleted(null);
//                    }
//                }
//                catch (Exception ex) {
//                    data.sendHeadersCallback.onCompleted(ex);
//                }
//            }
//        };
//
//        LineEmitter liner = new LineEmitter();
//        data.socket.setDataCallback(liner);
//        liner.setLineCallback(headerCallback);
        return true;
    }
}
