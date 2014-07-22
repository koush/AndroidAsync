package com.koushikdutta.async.http;

import java.util.List;

/**
 * Created by koush on 7/21/14.
 */
public class Headers {
    Multimap map = new Multimap();
    public Multimap getMultiMap() {
        return map;
    }

    public List<String> getAll(String header) {
        return map.get(header);
    }

    public String get(String header) {
        return map.getString(header);
    }

    public Headers set(String header, String value) {
        map.put(header, value);
        return this;
    }

    public Headers add(String header, String value) {
        map.add(header, value);
        return this;
    }

    public List<String> remove(String header) {
        return map.remove(header);
    }

    int responseCode;
    public int getResponseCode() {
        return responseCode;
    }
    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    String protocol;
    public String getProtocol() {
        return protocol;
    }
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    String responseMessage;

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }
}
