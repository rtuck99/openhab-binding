package com.qubular.vicare;

import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Installation;

import java.io.IOException;
import java.util.List;

public interface VicareService {
    List<Installation> getInstallations() throws AuthenticationException, IOException;

    List<Feature> getFeatures(long installationId, String gatewaySerial, String deviceId) throws AuthenticationException, IOException;
}
