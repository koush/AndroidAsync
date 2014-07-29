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
        public AsyncSocket socket();
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
        public DataEmitter emitter();
        public ResponseHead emitter(DataEmitter emitter);
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

    public static class OnExchangeHeaderData extends GetSocketData {
        public AsyncSocket socket;
        public ResponseHead response;
        public CompletedCallback sendHeadersCallback;
        public CompletedCallback receiveHeadersCallback;
    }

    public static class OnRequestSentData extends OnExchangeHeaderData {
    }

    public static class OnHeadersReceivedDataOnRequestSentData extends OnRequestSentData {
    }

    public static class OnBodyDataOnRequestSentData extends OnHeadersReceivedDataOnRequestSentData {
        public DataEmitter bodyEmitter;
    }

    public static class OnResponseCompleteDataOnRequestSentData extends OnBodyDataOnRequestSentData {
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
     * Called before when the headers are sent and received via the socket.
     * Implementers return true to denote they will manage header exchange.
     * @param data
     * @return
     */
    public boolean exchangeHeaders(OnExchangeHeaderData data);

    /**
     * Called once the headers and any optional request body has
     * been sent
     * @param data
     */
    public void onRequestSent(OnRequestSentData data);

    /**
     * Called once the headers have been received via the socket
     * @param data
     */
    public void onHeadersReceived(OnHeadersReceivedDataOnRequestSentData data);

    /**
     * Called before the body is decoded
     * @param data
     */
    public void onBodyDecoder(OnBodyDataOnRequestSentData data);

    /**
     * Called once the request is complete and response has been received,
     * or if an error occurred
     * @param data
     */
    public void onResponseComplete(OnResponseCompleteDataOnRequestSentData data);
}
