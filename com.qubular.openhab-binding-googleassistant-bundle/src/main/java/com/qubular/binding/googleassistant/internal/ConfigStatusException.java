package com.qubular.binding.googleassistant.internal;

import org.openhab.core.config.core.status.ConfigStatusMessage;

public class ConfigStatusException extends Exception {
    private final ConfigStatusMessage configStatusMessage;

    public ConfigStatusException(ConfigStatusMessage configStatusMessage) {
        super(configStatusMessage.toString());
        this.configStatusMessage = configStatusMessage;
    }

    public ConfigStatusException(ConfigStatusMessage configStatusMessage, Throwable t) {
        super(configStatusMessage.toString(), t);
        this.configStatusMessage = configStatusMessage;
    }

    public ConfigStatusMessage getConfigStatusMessage() {
        return configStatusMessage;
    }
}
