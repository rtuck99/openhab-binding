package com.qubular.binding.googleassistant.internal;

import java.util.ArrayList;
import java.util.List;

public interface DeviceRegistrationService extends EmbeddedAssistantService {
    class DeviceRegistrationException extends Exception {
        public DeviceRegistrationException(String message, Throwable cause) {
            super(message, cause);
        }

        public DeviceRegistrationException(String message) {
            super(message);
        }
    }

    class DeviceModel {
        public String name;
        public String deviceModelId;
        public String projectId;
        public String deviceType;
        public List<String> traits = new ArrayList<>();
        public Manifest manifest;
        public List<ExecutionMode> executionModes = new ArrayList<>();
    }

    enum ExecutionMode {
        MODE_UNSPECIFIED,
        DIRECT_RESPONSE
    }

    class Manifest {
        public String manufacturer;
        public String productName;
        public String deviceDescription;
    }

    class Device {
        public String id;
        public String modelId;
        public String nickname;
        public ClientType clientType;
    }

    enum ClientType {
        SDK_SERVICE,
        SDK_LIBRARY
    }

    List<DeviceModel> getDeviceModels() throws DeviceRegistrationException;

    void addDeviceModel(DeviceModel deviceModel) throws DeviceRegistrationException;

    List<Device> getDeviceInstances() throws DeviceRegistrationException;

    void addDevice(Device deviceInstance) throws DeviceRegistrationException;
}
