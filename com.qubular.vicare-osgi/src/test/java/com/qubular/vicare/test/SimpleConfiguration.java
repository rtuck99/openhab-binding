package com.qubular.vicare.test;

import com.qubular.vicare.VicareConfiguration;
import org.osgi.service.component.annotations.Component;

@Component(service = VicareConfiguration.class)
public class SimpleConfiguration implements VicareConfiguration {

    private String clientId;
    private String accessServerUri = DEFAULT_ACCESS_SERVER_URI;
    private String iotServerUri = DEFAULT_IOT_SERVER_URI;

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setAccessServerURI(String accessServerUri) {
        this.accessServerUri = accessServerUri;
    }

    public void setIOTServerURI(String iotServerUri) {
        this.iotServerUri = iotServerUri;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public String getAccessServerURI() {
        return accessServerUri;
    }

    @Override
    public String getIOTServerURI() {
        return iotServerUri;
    }
}
