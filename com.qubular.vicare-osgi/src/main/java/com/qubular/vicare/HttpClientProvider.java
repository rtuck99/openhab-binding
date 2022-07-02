package com.qubular.vicare;

import org.eclipse.jetty.client.HttpClient;

public interface HttpClientProvider {
    /**
     * @return A started HttpClient
     */
    HttpClient getHttpClient();
}
