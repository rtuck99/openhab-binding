package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.tokenstore.TokenEvent;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Gateway;
import com.qubular.vicare.model.Installation;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.*;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.type.ThingType;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
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

    private CompletableFuture<VicareBridgeHandler> bridgeHandler = new CompletableFuture<>();
    private ScheduledFuture<?> backgroundJob;
    private ServiceRegistration<EventHandler> eventHandlerRegistration;
    private EventAdmin eventAdmin;

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
                                    discoverHeating(installation, gateway, device, getBridgeHandler().getThing().getUID());
                                    break;
                                default:
                                    discoverUnknownDevice(installation, gateway, device, getBridgeHandler().getThing().getUID());
                                    break;
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

    private void discoverUnknownDevice(Installation installation, Gateway gateway, Device device, ThingUID bridgeId) {
        logger.info("Discovered unknown device type " + device.getDeviceType());
        ThingTypeUID thingTypeUID = new ThingTypeUID(BINDING_ID, VicareUtil.escapeUIDSegment(device.getDeviceType()));
        // prime the thingType registry
        ThingType thingType = getVicareServiceProvider().getVicareThingTypeProvider().fetchOrCreateThingType(getVicareServiceProvider().getThingTypeRegistry(), thingTypeUID);

        discoverDevice(new HashMap<>(), installation, gateway, device, bridgeId, String.format("Unrecognised %s device", device.getDeviceType()),
                       thingTypeUID);
    }

    private void discoverHeating(Installation installation, Gateway gateway, Device device, ThingUID bridgeId) {
        Map<String, Object> props = new HashMap<>();
        if (device.getBoilerSerial() != null) {
            props.put(PROPERTY_BOILER_SERIAL, device.getBoilerSerial());
        }
        if (device.getModelId() != null) {
            props.put(PROPERTY_MODEL_ID, device.getModelId());
        }
        discoverDevice(props, installation, gateway, device, bridgeId, null, THING_TYPE_HEATING);
    }

    private void discoverDevice(Map<String, Object> props, Installation installation, Gateway gateway, Device device, ThingUID bridgeId, @Nullable String label, ThingTypeUID thingType) {
        String uniqueId = VicareUtil.encodeThingUniqueId(installation.getId(),
                gateway.getSerial(),
                device.getId());
        String thingId = VicareUtil.encodeThingId(installation.getId(),
                gateway.getSerial(),
                device.getId());
        ThingUID thingUid = new ThingUID(thingType,
                                         bridgeId,
                                         thingId);
        // These have to be non-null in order to resolve the device
        props.put(PROPERTY_DEVICE_UNIQUE_ID, uniqueId);
        props.put(PROPERTY_GATEWAY_SERIAL, device.getGatewaySerial());
        props.put(PROPERTY_DEVICE_TYPE, device.getDeviceType());
        DiscoveryResultBuilder builder = DiscoveryResultBuilder.create(thingUid)
                .withBridge(bridgeId)
                .withProperties(props)
                .withRepresentationProperty(PROPERTY_DEVICE_UNIQUE_ID);
        if (label != null) {
            builder.withLabel(label);
        }

        DiscoveryResult result = builder.build();
        logger.info("Discovered {}", uniqueId);
        if (eventAdmin != null) {
            // post an event to update any properties of the thing if it already exists
            eventAdmin.postEvent(new Event(DeviceDiscoveryEvent.generateTopic(thingUid), props));
        }
        this.thingDiscovered(result);
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        this.bridgeHandler.complete((VicareBridgeHandler) handler);
    }

    @Override
    public @Nullable VicareBridgeHandler getThingHandler() {
        return bridgeHandler.join();
    }

    private VicareServiceProvider getVicareServiceProvider() {
        return getThingHandler().getVicareServiceProvider();
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
