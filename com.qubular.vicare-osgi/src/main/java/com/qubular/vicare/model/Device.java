package com.qubular.vicare.model;

public class Device {
    public static final String DEVICE_TYPE_HEATING = "heating";
    private String gatewaySerial;
    private String id;
    private String boilerSerial;
    private String modelId;
    private String status;
    private String deviceType;

    /** For Gson */
    Device() {
    }

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
