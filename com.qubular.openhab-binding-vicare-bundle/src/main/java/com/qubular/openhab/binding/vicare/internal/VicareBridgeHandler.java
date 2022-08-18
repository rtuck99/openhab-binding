package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.internal.configuration.SimpleConfiguration;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Feature;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_DEVICE_UNIQUE_ID;
import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_FEATURE_NAME;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.decodeThingUniqueId;

public class VicareBridgeHandler extends BaseBridgeHandler {
    private static final Logger logger = LoggerFactory.getLogger(VicareBridgeHandler.class);
    private final ThingRegistry thingRegistry;
    private final VicareConfiguration config;

    private final VicareService vicareService;
    private List<Feature> cachedResponse = null;

    private Instant responseTimestamp = Instant.MIN;

    private static final int REQUEST_INTERVAL_SECS = 90;

    /**
     * @param thingRegistry
     * @param bridge
     * @see BaseThingHandler
     */
    public VicareBridgeHandler(VicareService vicareService,
                               ThingRegistry thingRegistry,
                               Bridge bridge,
                               VicareConfiguration config) {
        super(bridge);
        this.vicareService = vicareService;
        this.thingRegistry = thingRegistry;
        this.config = config;
        ((SimpleConfiguration)config).setConfigurationParameters(getConfig().getProperties());
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        ((SimpleConfiguration) config).setConfigurationParameters(configurationParameters);
    }

    public Optional<Feature> handleBridgedDeviceCommand(ChannelUID channelUID, Command command) throws AuthenticationException, IOException {
        logger.debug("Handling command {} for channel {} from thing {}", command, channelUID, channelUID.getThingUID());
        Thing targetThing = thingRegistry.get(channelUID.getThingUID());
        if (command instanceof RefreshType) {
            try {
                Channel channel = targetThing.getChannel(channelUID);
                List<Feature> features = getFeatures(targetThing);
                String featureName = channel.getProperties().get(PROPERTY_FEATURE_NAME);
                if (getThing().getStatus() != ThingStatus.ONLINE) {
                    updateStatus(ThingStatus.ONLINE);
                }
                return features.stream()
                        .filter(f -> f.getName().equals(featureName))
                        .findAny();
            } catch (AuthenticationException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to authenticate with Viessmann API: " + e.getMessage());
                throw e;
            } catch (IOException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to communicate with Viessmann API: " + e.getMessage());
                throw e;
            }
        } else {
            return Optional.empty();
        }
    }

    private List<Feature> getFeatures(Thing thing) throws AuthenticationException, IOException {
        Instant now = Instant.now();
        if (now.isBefore(responseTimestamp.plusSeconds(REQUEST_INTERVAL_SECS))) {
            return cachedResponse;
        } else {
            VicareUtil.IGD s = decodeThingUniqueId(thing.getProperties().get(PROPERTY_DEVICE_UNIQUE_ID));
            List<Feature> features = vicareService.getFeatures(s.installationId, s.gatewaySerial, s.deviceId);
            synchronized (this) {
                cachedResponse = features;
                responseTimestamp = Instant.now();
                return cachedResponse;
            }
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(VicareDiscoveryService.class);
    }

    VicareService getVicareService() {
        return vicareService;
    }
}
