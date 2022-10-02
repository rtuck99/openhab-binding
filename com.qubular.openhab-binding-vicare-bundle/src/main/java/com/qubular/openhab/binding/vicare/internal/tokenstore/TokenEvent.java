package com.qubular.openhab.binding.vicare.internal.tokenstore;

public class TokenEvent {
    public static final String TYPE = TokenEvent.class.getName();
    public static final String TOPIC_NEW_ACCESS_TOKEN = TokenEvent.class.getName().replaceAll("\\.","/") + "/NEW_ACCESS_TOKEN";
}
