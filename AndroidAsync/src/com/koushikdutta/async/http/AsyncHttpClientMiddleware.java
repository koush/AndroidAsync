package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.util.UntypedHashtable;

/**
 * AsyncHttpClientMiddleware is used by AsyncHttpClient to
 * inspect, manipulate, and handle http requests.
 */
public interface AsyncHttpClientMiddleware {
    public interface ResponseHead  {
        public String protocol();
        public String message();
        public int code();
        public ResponseHead protocol(String protocol);
        public ResponseHead message(String message);
        public ResponseHead code(int code);
        public Headers headers();
        public ResponseHead headers(Headers headers);
        public DataSink sink();
        public ResponseHead sink(DataSink sink);
    }

    public static class OnRequestData {
        public UntypedHashtable state = new UntypedHashtable();
        public AsyncHttpRequest request;
    }

    public static class GetSocketData extends OnRequestData {
        public ConnectCallback connectCallback;
        public Cancellable socketCancellable;
        public String protocol;
    }

    public static class SendHeaderData extends GetSocketData {
        public AsyncSocket socket;
        public ResponseHead response;
        public CompletedCallback sendHeadersCallback;
    }

    public static class OnHeadersReceivedData extends SendHeaderData {
//        public Headers headers;
    }

    public static class OnBodyData extends OnHeadersReceivedData {
        public DataEmitter bodyEmitter;
    }

    public static class OnRequestCompleteData extends OnBodyData {
        public Exception exception;
    }

    /**
     * Called immediately upon request execution
     * @param data
     */
    public void onRequest(OnRequestData data);

    /**
     * Called to retrieve the socket that will fulfill this request
     * @param data
     * @return
     */
    public Cancellable getSocket(GetSocketData data);

    /**
     * Called before the headers are sent via the socket
     * @param data
     * @return
     */
    public boolean sendHeaders(SendHeaderData data);

    /**
     * Called once the headers have been received via the socket
     * @param data
     */
    public void onHeadersReceived(OnHeadersReceivedData data);

    /**
     * Called before the body is decoded
     * @param data
     */
    public void onBodyDecoder(OnBodyData data);

    /**
     * Called once the request is complete
     * @param data
     */
    public void onRequestComplete(OnRequestCompleteData data);
}
