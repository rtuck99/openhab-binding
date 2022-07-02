package com.qubular.vicare.test;

import com.qubular.vicare.HttpClientProvider;
import org.eclipse.jetty.client.HttpClient;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(service = HttpClientProvider.class)
public class SimpleHttpClientProvider implements HttpClientProvider {
    private final HttpClient httpClient;

    @Activate
    public SimpleHttpClientProvider() {
        this.httpClient = new HttpClient();
        try {
            this.httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public HttpClient getHttpClient() {
        return httpClient;
    }
}
