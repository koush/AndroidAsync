package com.koushikdutta.async.stetho;

import com.facebook.stetho.inspector.network.NetworkEventReporter;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.SimpleMiddleware;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Created by koush on 2/18/15.
 */
public class StethoMiddleware extends SimpleMiddleware {
    private final NetworkEventReporterWrapper eventReporter = NetworkEventReporterWrapper.get();

    private static class AsyncInspectorRequest implements NetworkEventReporter.InspectorRequest {
        AsyncHttpRequest request;
        String id = UUID.randomUUID().toString();
        Header[] headers;
        AsyncInspectorResponse response;

        public AsyncInspectorRequest(AsyncHttpRequest request) {
            this.request = request;
            headers = request.asHttpRequest().getAllHeaders();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String friendlyName() {
            return request.getLogTag() != null ? request.getLogTag() : id;
        }

        @Nullable
        @Override
        public Integer friendlyNameExtra() {
            return null;
        }

        @Override
        public String url() {
            return request.getUri().toString();
        }

        @Override
        public String method() {
            return request.getMethod();
        }

        @Nullable
        @Override
        public byte[] body() throws IOException {
            return request.getBody() != null ? request.getBody().toString().getBytes("UTF-8") : null;
        }

        @Override
        public int headerCount() {
            return headers.length;
        }

        @Override
        public String headerName(int i) {
            return headers[i].getName();
        }

        @Override
        public String headerValue(int i) {
            return headers[i].getValue();
        }

        @Nullable
        @Override
        public String firstHeaderValue(String s) {
            return request.getHeaders().get(s);
        }
    }

    private static class AsyncInspectorResponse implements NetworkEventReporter.InspectorResponse {
        ResponseHead head;
        AsyncInspectorRequest request;
        Header[] headers;

        public AsyncInspectorResponse(ResponseHead head, AsyncInspectorRequest request) {
            this.request = request;
            this.head = head;
            ArrayList<Header> a = new ArrayList<Header>();
            for (NameValuePair nvp: head.headers().getMultiMap()) {
                a.add(new BasicHeader(nvp.getName(), nvp.getValue()));
            }
            headers = a.toArray(new Header[a.size()]);
        }

        @Override
        public String requestId() {
            return request.id();
        }

        @Override
        public int statusCode() {
            return head.code();
        }

        @Override
        public String reasonPhrase() {
            return head.message();
        }

        @Override
        public boolean connectionReused() {
            return false;
        }

        @Override
        public int connectionId() {
            return 0;
        }

        @Override
        public boolean fromDiskCache() {
            return false;
        }

        @Override
        public String url() {
            return request.url();
        }

        @Override
        public int headerCount() {
            return headers.length;
        }

        @Override
        public String headerName(int i) {
            return headers[i].getName();
        }

        @Override
        public String headerValue(int i) {
            return headers[i].getValue();
        }

        @Nullable
        @Override
        public String firstHeaderValue(String s) {
            return head.headers().get(s);
        }
    }

    @Override
    public void onRequest(OnRequestData data) {
        super.onRequest(data);

        if (!data.request.getUri().getScheme().startsWith("http"))
            return;

        AsyncInspectorRequest inspect = new AsyncInspectorRequest(data.request);
        data.state.put("inspect", inspect);
        eventReporter.requestWillBeSent(inspect);
    }

    @Override
    public void onHeadersReceived(OnHeadersReceivedDataOnRequestSentData data) {
        super.onHeadersReceived(data);
        AsyncInspectorRequest inspect = data.state.get("inspect");
        if (inspect == null)
            return;

        inspect.response = new AsyncInspectorResponse(data.response, inspect);
        eventReporter.responseHeadersReceived(inspect.response);
    }

    @Override
    public void onBodyDecoder(OnBodyDataOnRequestSentData data) {
        super.onBodyDecoder(data);

        AsyncInspectorRequest inspect = data.state.get("inspect");
        if (inspect == null)
            return;

        String ct = data.response.headers().get("Content-Type");
        boolean isImage = ct != null && ct.startsWith("image/");
        DataEmitter emitter = eventReporter.interpretResponseEmitter(inspect.id(), data.bodyEmitter, isImage);
        if (emitter != null)
            data.bodyEmitter = emitter;
    }
}
