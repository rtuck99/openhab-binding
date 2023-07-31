package com.qubular.vicare;

import java.io.IOException;

public class VicareServiceException extends IOException {
    private final VicareError vicareError;

    public VicareServiceException(VicareError vicareError) {
        super (formatErrorMessage(vicareError));
        this.vicareError = vicareError;
    }

    public VicareError getVicareError() {
        return vicareError;
    }

    private static String formatErrorMessage(VicareError vicareError) {
        String msg = String.format("API returned %d:%s - %s", vicareError.getStatusCode(), vicareError.getErrorType(), vicareError.getMessage());
        if (vicareError.getExtendedPayload() != null) {
            msg += String.format(" - %d:%s", vicareError.getExtendedPayload().getCode(), vicareError.getExtendedPayload().getReason());
        }
        return msg;
    }
}
