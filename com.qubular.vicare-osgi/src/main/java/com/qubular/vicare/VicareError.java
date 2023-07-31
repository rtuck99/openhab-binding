package com.qubular.vicare;

public class VicareError {
    public static final String ERROR_TYPE_DEVICE_COMMUNICATION_ERROR = "DEVICE_COMMUNICATION_ERROR";
    private int statusCode;
    private String errorType;
    private String message;
    private String viErrorId;
    private ExtendedPayload extendedPayload;

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getMessage() {
        return message;
    }

    public ExtendedPayload getExtendedPayload() {
        return extendedPayload;
    }

    public static class ExtendedPayload {
        public static final String REASON_GATEWAY_OFFLINE = "GATEWAY_OFFLINE";
        private long limitReset;
        private String httpStatusCode;
        private String code;
        private String reason;

        public long getLimitReset() {
            return limitReset;
        }

        public String getHttpStatusCode() {
            return httpStatusCode;
        }

        public int getCode() {
            try {
                return Integer.valueOf(code);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "ExtendedPayload{" +
                    "limitReset=" + limitReset +
                    ", httpStatusCode='" + httpStatusCode + '\'' +
                    ", code='" + code + '\'' +
                    ", reason='" + reason + '\'' +
                    '}';
        }
    }
}
