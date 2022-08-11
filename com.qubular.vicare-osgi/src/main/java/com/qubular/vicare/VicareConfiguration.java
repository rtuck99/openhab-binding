package com.qubular.vicare;

import org.osgi.service.component.annotations.ComponentPropertyType;

import java.net.URI;

public interface VicareConfiguration {
    String DEFAULT_ACCESS_SERVER_URI = "https://iam.viessmann.com/idp/v2/token";
    String DEFAULT_IOT_SERVER_URI = "https://api.viessmann.com/iot/v1/";
    String getClientId();

    String getAccessServerURI();

    String getIOTServerURI();
}
