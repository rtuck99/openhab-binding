package com.qubular.vicare;

import org.osgi.service.component.annotations.ComponentPropertyType;

import java.io.File;
import java.net.URI;

public interface VicareConfiguration {
    String DEFAULT_ACCESS_SERVER_URI = "https://iam.viessmann.com/idp/v3/token";
    String DEFAULT_IOT_SERVER_URI = "https://api.viessmann.com/iot/v1/";
    String getClientId();

    String getAccessServerURI();

    String getIOTServerURI();

    default File getResponseCaptureFolder() {
        return null;
    }

    @Deprecated
    default File getResponseCaptureFile() {
        return null;
    }

    default boolean isResponseCaptureEnabled() {
        return getResponseCaptureFolder() != null;
    }

    default File getResponseInjectionFile() {
        return null;
    }

    default boolean isResponseInjectionEnabled() {
        return getResponseInjectionFile() != null;
    }

    default Long getDebugInjectedInstallationId() {
        return null;
    }

    default String getDebugInjectedGatewaySerial() {
        return null;
    }
}
