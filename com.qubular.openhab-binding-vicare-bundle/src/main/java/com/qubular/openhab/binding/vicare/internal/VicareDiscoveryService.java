package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Gateway;
import com.qubular.vicare.model.Installation;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_DEVICE_UNIQUE_ID;
import static com.qubular.openhab.binding.vicare.internal.VicareConstants.THING_TYPE_HEATING;
import static com.qubular.vicare.model.Device.DEVICE_TYPE_HEATING;

@Component(service = DiscoveryService.class)
public class VicareDiscoveryService extends AbstractDiscoveryService {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.emptySet();
    private static final int DISCOVERY_TIMEOUT_SECS = 10;

    private static final Logger logger = LoggerFactory.getLogger(VicareDiscoveryService.class);
    private final VicareService vicareService;
    private ThingUID bridgeId;

    public VicareDiscoveryService(VicareService vicareService, ThingUID bridgeId) {
        super(SUPPORTED_THING_TYPES, DISCOVERY_TIMEOUT_SECS, false);
        this.vicareService = vicareService;
        this.bridgeId = bridgeId;
    }

    @Override
    protected void startScan() {
        scheduler.submit(() -> {
            logger.info("Starting Viessmann bridge scan");
            try {
                List<Installation> installations = vicareService.getInstallations();
                for (Installation installation : installations) {
                    for (Gateway gateway : installation.getGateways()) {
                        for (Device device : gateway.getDevices()) {
                            switch (device.getDeviceType()) {
                                case DEVICE_TYPE_HEATING:
                                    discoverHeating(installation, gateway, device, bridgeId);
                                    break;
                                default:
                                    logger.info("Ignoring unsupported device type " + device.getDeviceType());
                            }
                        }
                    }
                }
            } catch (AuthenticationException e) {
                if (scanListener != null) {
                    logger.warn("Authentication problem scanning Viessmann API:" + e.getMessage());
                    scanListener.onErrorOccurred(e);
                }
            } catch (IOException e) {
                if (scanListener != null) {
                    logger.warn("IO problem scanning Viessmann API:" + e.getMessage());
                    scanListener.onErrorOccurred(e);
                }
            } finally {
                if (scanListener != null) {
                    scanListener.onFinished();
                }
            }
        });
    }

    private void discoverHeating(Installation installation, Gateway gateway, Device device, ThingUID bridgeId) {
        String uniqueId = VicareUtil.encodeThingUniqueId(installation.getId(),
                gateway.getSerial(),
                device.getId());
        String thingId = VicareUtil.encodeThingId(installation.getId(),
                gateway.getSerial(),
                device.getId());
        ThingUID thingUid = new ThingUID(THING_TYPE_HEATING,
                bridgeId,
                thingId);
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUid)
                .withBridge(bridgeId)
                .withProperty(PROPERTY_DEVICE_UNIQUE_ID, uniqueId)
                .withRepresentationProperty(PROPERTY_DEVICE_UNIQUE_ID)
                .build();
        this.thingDiscovered(result);
    }
}
