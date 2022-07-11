package com.qubular.vicare.model;

import java.time.Instant;
import java.util.List;

public class Gateway {
    private String serial;
    private String version;
    private int firmwareUpdateFailureCounter;
    private Instant lastStatusChangedAt;
    private String aggregatedStatus;
    private String gatewayType;
    private long installationId;
    private List<Device> devices;

    /** For Gson */
    Gateway() {
    }

    public Gateway(String serial, String version, int firmwareUpdateFailureCounter, Instant lastStatusChangedAt, String aggregatedStatus, String gatewayType, long installationId, List<Device> devices) {
        this.serial = serial;
        this.version = version;
        this.firmwareUpdateFailureCounter = firmwareUpdateFailureCounter;
        this.lastStatusChangedAt = lastStatusChangedAt;
        this.aggregatedStatus = aggregatedStatus;
        this.gatewayType = gatewayType;
        this.installationId = installationId;
        this.devices = devices;
    }

    public String getSerial() {
        return serial;
    }

    public String getVersion() {
        return version;
    }

    public int getFirmwareUpdateFailureCounter() {
        return firmwareUpdateFailureCounter;
    }

    public Instant getLastStatusChangedAt() {
        return lastStatusChangedAt;
    }

    public String getAggregatedStatus() {
        return aggregatedStatus;
    }

    public String getGatewayType() {
        return gatewayType;
    }

    public long getInstallationId() {
        return installationId;
    }

    public List<Device> getDevices() {
        return devices;
    }
}
