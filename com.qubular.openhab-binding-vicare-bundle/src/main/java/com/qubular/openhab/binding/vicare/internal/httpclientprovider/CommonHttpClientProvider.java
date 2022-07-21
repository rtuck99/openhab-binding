package com.qubular.openhab.binding.vicare.internal.httpclientprovider;

import com.qubular.vicare.HttpClientProvider;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = HttpClientProvider.class)
public class CommonHttpClientProvider implements HttpClientProvider {
    private final HttpClientFactory httpClientFactory;

    @Activate
    public CommonHttpClientProvider(@Reference HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public HttpClient getHttpClient() {
        return httpClientFactory.getCommonHttpClient();
    }
}
