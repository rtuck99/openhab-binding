package com.qubular.glowmarkt;

import org.eclipse.jetty.client.HttpClient;

import java.net.URI;

public interface GlowmarktSettings {
    URI DEFAULT_URI_ENDPOINT = URI.create("https://api.glowmarkt.com/api/v0-1/");
    String DEFAULT_APPLICATION_ID = "b0f1b774-a586-4f72-9edd-27ead8aa7a8d";

    String getApplicationId();

    URI getApiEndpoint();

    HttpClient getHttpClient();
}
