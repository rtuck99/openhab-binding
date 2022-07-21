package com.qubular.openhab.binding.vicare.internal;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class VicareUtil {
    public static class IGD {
        long installationId;
        String gatewaySerial;
        String deviceId;
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
