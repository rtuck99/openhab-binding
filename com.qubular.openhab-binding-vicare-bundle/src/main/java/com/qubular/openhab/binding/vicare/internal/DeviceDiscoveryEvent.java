package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.thing.ThingUID;

public class DeviceDiscoveryEvent {
    public static final String TYPE = DeviceDiscoveryEvent.class.getName();

    private static final String TOPIC_DEVICE_DISCOVERED = TYPE.replaceAll("\\.", "/") + "/%s/deviceDiscovered";

    public static final String generateTopic(ThingUID uid) {
        return String.format(TOPIC_DEVICE_DISCOVERED, toToken(uid));
    }

    private static String toToken(ThingUID uid) {
        return uid.getAsString().replaceAll("[^A-Za-z0-9_-]","_");
    }
}
