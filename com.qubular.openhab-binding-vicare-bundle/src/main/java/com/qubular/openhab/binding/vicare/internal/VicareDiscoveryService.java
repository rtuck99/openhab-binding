package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.internal.tokenstore.TokenEvent;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Gateway;
import com.qubular.vicare.model.Installation;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.*;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.vicare.model.Device.DEVICE_TYPE_HEATING;
import static java.util.Collections.*;
import static org.osgi.service.event.EventConstants.EVENT_TOPIC;

public class VicareDiscoveryService extends AbstractDiscoveryService
    implements ThingHandlerService {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = emptySet();
    private static final int DISCOVERY_TIMEOUT_SECS = 10;

    private static final Logger logger = LoggerFactory.getLogger(VicareDiscoveryService.class);

    private CompletableFuture<BridgeHandler> bridgeHandler = new CompletableFuture<>();
    private ScheduledFuture<?> backgroundJob;
    private ServiceRegistration<EventHandler> eventHandlerRegistration;
    private EventAdmin eventAdmin;

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
                logger.warn("Authentication problem scanning Viessmann API:" + e.getMessage());
                if (scanListener != null) {
                    scanListener.onErrorOccurred(e);
                }
            } catch (IOException e) {
                logger.warn("IO problem scanning Viessmann API:" + e.getMessage());
                if (scanListener != null) {
                    scanListener.onErrorOccurred(e);
                }
            } catch (RuntimeException e) {
                logger.warn("Unexpected error occurred scanning Viessmann API", e);
                if (scanListener != null) {
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
        Map<String, Object> props = new HashMap<>();
        // These have to be non-null in order to resolve the device
        props.put(PROPERTY_DEVICE_UNIQUE_ID, uniqueId);
        props.put(PROPERTY_GATEWAY_SERIAL, device.getGatewaySerial());
        props.put(PROPERTY_DEVICE_TYPE, device.getDeviceType());
        if (device.getBoilerSerial() != null) {
            props.put(PROPERTY_BOILER_SERIAL, device.getBoilerSerial());
        }
        if (device.getModelId() != null) {
            props.put(PROPERTY_MODEL_ID, device.getModelId());
        }
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUid)
                .withBridge(bridgeId)
                .withProperties(props)
                .withRepresentationProperty(PROPERTY_DEVICE_UNIQUE_ID)
                .build();
        logger.info("Discovered {}", uniqueId);
        if (eventAdmin != null) {
            eventAdmin.postEvent(new Event(DeviceDiscoveryEvent.generateTopic(thingUid), props));
        }
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
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(EVENT_TOPIC, TokenEvent.TOPIC_NEW_ACCESS_TOKEN);
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        eventHandlerRegistration = bundleContext.registerService(EventHandler.class, new TokenEventHandler(), properties);
        eventAdmin = bundleContext.getService(bundleContext.getServiceReference(EventAdmin.class));
    }

    @Override
    public void deactivate() {
        eventHandlerRegistration.unregister();
        ThingHandlerService.super.deactivate();
        stopBackgroundDiscovery();
    }

    private class TokenEventHandler implements EventHandler {
        @Override
        public void handleEvent(org.osgi.service.event.Event event) {
            logger.info("Received token event.");
            VicareDiscoveryService.this.startScan();
        }
    }

    boolean isBackgroundDiscoveryRunning() {
        return !(backgroundJob.isDone() || backgroundJob.isCancelled());
    }
}
