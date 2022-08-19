package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Gateway;
import com.qubular.vicare.model.Installation;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_DEVICE_UNIQUE_ID;
import static com.qubular.openhab.binding.vicare.internal.VicareConstants.THING_TYPE_HEATING;
import static com.qubular.vicare.model.Device.DEVICE_TYPE_HEATING;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class VicareDiscoveryService extends AbstractDiscoveryService
    implements ThingHandlerService {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = emptySet();
    private static final int DISCOVERY_TIMEOUT_SECS = 10;

    private static final Logger logger = LoggerFactory.getLogger(VicareDiscoveryService.class);

    private CompletableFuture<BridgeHandler> bridgeHandler = new CompletableFuture<>();
    private ScheduledFuture<?> backgroundJob;

    /** Invoked by the bridge handler factory */
    public VicareDiscoveryService() {
        super(SUPPORTED_THING_TYPES, DISCOVERY_TIMEOUT_SECS, true);
        logger.info("Created Vicare Discovery Service");
        // activate manually 'cos not part of OSGI services
        // Potential race condition here because this invokes discovery before our caller
        // has set listener
        activate(emptyMap());
    }

    private VicareService getVicareService() {
        return getBridgeHandler().getVicareService();
    }

    private VicareBridgeHandler getBridgeHandler() {
        return (VicareBridgeHandler) bridgeHandler.join();
    }

    @Override
    protected synchronized void startBackgroundDiscovery() {
        logger.info("Starting background discovery");
        if (backgroundJob == null) {
            backgroundJob = scheduler.scheduleAtFixedRate(scanJob(), 0, 1, TimeUnit.HOURS);
        }
    }

    @Override
    protected synchronized void stopBackgroundDiscovery() {
        logger.info("Cancelling background discovery");
        if (backgroundJob != null) {
            backgroundJob.cancel(false);
            backgroundJob = null;
        }
    }

    @Override
    protected void startScan() {
        scheduler.submit(scanJob());
    }

    private Runnable scanJob() {
        return () -> {
            logger.info("Starting Viessmann bridge scan");
            try {
                List<Installation> installations = getVicareService().getInstallations();
                for (Installation installation : installations) {
                    for (Gateway gateway : installation.getGateways()) {
                        for (Device device : gateway.getDevices()) {
                            switch (device.getDeviceType()) {
                                case DEVICE_TYPE_HEATING:
                                    discoverHeating(installation, gateway, device, bridgeHandler.join().getThing().getUID());
                                    break;
                                default:
                                    logger.info("Ignoring unsupported device type " + device.getDeviceType());
                            }
                        }
                    }
                }
                getBridgeHandler().updateStatus(ThingStatus.ONLINE);
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
        };
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
        logger.info("Discovered {}", uniqueId);
        this.thingDiscovered(result);
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        this.bridgeHandler.complete((BridgeHandler) handler);
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler.join();
    }

    @Override
    public void activate() {
        ThingHandlerService.super.activate();
    }

    @Override
    public void deactivate() {
        ThingHandlerService.super.deactivate();
        stopBackgroundDiscovery();
    }
}
