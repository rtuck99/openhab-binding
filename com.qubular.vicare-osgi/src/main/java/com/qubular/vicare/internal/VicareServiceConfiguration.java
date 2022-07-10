package com.qubular.vicare.internal;

import com.qubular.vicare.VicareService;
import org.osgi.service.cm.Configuration;

import java.net.URI;

import static com.qubular.vicare.VicareService.CONFIG_ACCESS_SERVER_URI;
import static java.util.Optional.ofNullable;

class VicareServiceConfiguration {
    private final Configuration configuration;

    public VicareServiceConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public URI getAccessServerURI() {
        return ofNullable(configuration.getProperties()).map(d -> d.get(CONFIG_ACCESS_SERVER_URI))
                .map(String::valueOf)
                .map(URI::create)
                .orElse(URI.create("https://iam.viessmann.com/idp/v2/token"));
    }

    public String getClientId() {
        return ofNullable(configuration.getProperties())
                .map(d -> (String) d.get(VicareService.CONFIG_CLIENT_ID))
                .orElseThrow(() -> new IllegalStateException("Client ID is not configured"));
    }

    public URI getIOTServerURI() {
        return ofNullable(configuration.getProperties()).map(d -> d.get(VicareService.CONFIG_IOT_SERVER_URI))
                .map(String::valueOf)
                .map(URI::create)
                .orElse(URI.create("https://api.viessmann.com/iot/v1/"));
    }
}
