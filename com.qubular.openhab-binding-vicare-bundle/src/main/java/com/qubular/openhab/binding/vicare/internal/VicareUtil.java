package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.thing.Thing;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_DEVICE_UNIQUE_ID;

public class VicareUtil {
    public static class IGD {
        long installationId;

        String gatewaySerial;
        String deviceId;
    }
    public static String getDeviceUniqueId(Thing t) {
        // Hidden configuration option for testing purposes
        String deviceUniqueId = (String) t.getConfiguration().get("deviceUniqueId");
        if (deviceUniqueId != null) {
            return deviceUniqueId;
        }
        return t.getProperties().get(PROPERTY_DEVICE_UNIQUE_ID);
    }

    public static String encodeThingId(long installationId,
                                       String gatewaySerial,
                                       String deviceId) {
        return UUID.nameUUIDFromBytes(encodeThingUniqueId(installationId, gatewaySerial, deviceId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String encodeThingUniqueId(long installationId, String gatewaySerial, String deviceId) {
        return String.format("%s/%s/%s", installationId, gatewaySerial, deviceId);
    }

    public static IGD decodeThingUniqueId(String uniqueId) {
        IGD decoded = new IGD();
        String[] split = uniqueId.split("/");
        decoded.installationId = Long.valueOf(split[0]);
        decoded.gatewaySerial = split[1];
        decoded.deviceId = split[2];
        return decoded;
    }

    public static String escapeUIDSegment(String s) {
        return s == null ? null : s.replaceAll("\\W", "_");
    }
}
