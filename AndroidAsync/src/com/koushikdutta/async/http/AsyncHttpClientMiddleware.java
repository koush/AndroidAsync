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
    interface ResponseHead  {
        AsyncSocket socket();
        String protocol();
        String message();
        int code();
        ResponseHead protocol(String protocol);
        ResponseHead message(String message);
        ResponseHead code(int code);
        Headers headers();
        ResponseHead headers(Headers headers);
        DataSink sink();
        ResponseHead sink(DataSink sink);
        DataEmitter emitter();
        ResponseHead emitter(DataEmitter emitter);
    }

    class OnRequestData {
        public UntypedHashtable state = new UntypedHashtable();
        public AsyncHttpRequest request;
    }

    class GetSocketData extends OnRequestData {
        public ConnectCallback connectCallback;
        public Cancellable socketCancellable;
        public String protocol;
    }

    class OnExchangeHeaderData extends GetSocketData {
        public AsyncSocket socket;
        public ResponseHead response;
        public CompletedCallback sendHeadersCallback;
        public CompletedCallback receiveHeadersCallback;
    }

    class OnRequestSentData extends OnExchangeHeaderData {
    }

    class OnHeadersReceivedData extends OnRequestSentData {
    }

    class OnBodyDecoderData extends OnHeadersReceivedData {
        public DataEmitter bodyEmitter;
    }

    class OnResponseReadyData extends OnBodyDecoderData {
    }

    class OnResponseCompleteData extends OnResponseReadyData {
        public Exception exception;
    }

    /**
     * Called immediately upon request execution
     * @param data
     */
    void onRequest(OnRequestData data);

    /**
     * Called to retrieve the socket that will fulfill this request
     * @param data
     * @return
     */
    Cancellable getSocket(GetSocketData data);

    /**
     * Called before when the headers are sent and received via the socket.
     * Implementers return true to denote they will manage header exchange.
     * @param data
     * @return
     */
    boolean exchangeHeaders(OnExchangeHeaderData data);

    /**
     * Called once the headers and any optional request body has
     * been sent
     * @param data
     */
    void onRequestSent(OnRequestSentData data);

    /**
     * Called once the headers have been received via the socket
     * @param data
     */
    void onHeadersReceived(OnHeadersReceivedData data);

    /**
     * Called before the body is decoded
     * @param data
     */
    void onBodyDecoder(OnBodyDecoderData data);

    /**
     * Called before the response is returned to the client. Return a new AsyncHttpRequest
     * to end the current request and start a new one. Can be used to implement redirect strategies
     * or multileg authentication, such as digest.
     * @param data
     * @return
     */
    AsyncHttpRequest onResponseReady(OnResponseReadyData data);

    /**
     * Called once the request is complete and response has been received,
     * or if an error occurred
     * @param data
     */
    void onResponseComplete(OnResponseCompleteData data);
}
