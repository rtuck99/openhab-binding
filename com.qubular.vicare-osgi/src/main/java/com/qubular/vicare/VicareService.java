package com.qubular.vicare;

import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Installation;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface VicareService {
    List<Installation> getInstallations() throws AuthenticationException, IOException;

    List<Feature> getFeatures(long installationId, String gatewaySerial, String deviceId) throws AuthenticationException, IOException;

    void sendCommand(URI uri, Map<String, Object> params) throws AuthenticationException, IOException, CommandFailureException;
}
