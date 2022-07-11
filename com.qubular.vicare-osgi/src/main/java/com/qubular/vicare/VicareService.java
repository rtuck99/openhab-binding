package com.qubular.vicare;

import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Installation;

import java.io.IOException;
import java.util.List;

public interface VicareService {
    String CONFIG_PID = "com.qubular.vicare.VicareService";

    String CONFIG_ACCESS_SERVER_URI = "accessServerUri";

    String CONFIG_CLIENT_ID = "clientId";
    String CONFIG_IOT_SERVER_URI = "iotServerUri";

    List<Installation> getInstallations() throws AuthenticationException, IOException;

    List<Feature> getFeatures(long installationId, String gatewaySerial, String deviceId) throws AuthenticationException, IOException;
}
