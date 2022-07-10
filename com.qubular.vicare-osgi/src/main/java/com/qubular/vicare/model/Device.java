package com.qubular.vicare.model;

public class Device {
    private final String gatewaySerial;
    private final String id;
    private final String boilerSerial;
    private final String modelId;
    private final String status;
    private final String deviceType;

    public Device(String gatewaySerial, String id, String boilerSerial, String modelId, String status, String deviceType) {
        this.gatewaySerial = gatewaySerial;
        this.id = id;
        this.boilerSerial = boilerSerial;
        this.modelId = modelId;
        this.status = status;
        this.deviceType = deviceType;
    }

    public String getGatewaySerial() {
        return gatewaySerial;
    }

    public String getId() {
        return id;
    }

    public String getBoilerSerial() {
        return boilerSerial;
    }

    public String getModelId() {
        return modelId;
    }

    public String getStatus() {
        return status;
    }

    public String getDeviceType() {
        return deviceType;
    }
}
