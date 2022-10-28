package com.qubular.glowmarkt;

public class AuthenticationFailedException extends Exception {
    public AuthenticationFailedException(String message) {
        super(message);
    }

    public AuthenticationFailedException(String message, Throwable t) {
        super(message, t);
    }
}
