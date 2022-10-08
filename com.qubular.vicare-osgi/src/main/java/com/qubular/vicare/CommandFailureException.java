package com.qubular.vicare;

public class CommandFailureException extends Exception {
    private final String reason;

    public CommandFailureException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
