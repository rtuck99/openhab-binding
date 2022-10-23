package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.configuration.SimpleConfiguration;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.CommandFailureException;
import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.decodeThingUniqueId;
import static java.util.Optional.empty;

public class VicareBridgeHandler extends BaseBridgeHandler {
    private static final Logger logger = LoggerFactory.getLogger(VicareBridgeHandler.class);
    public static final int POLLING_STARTUP_DELAY_SECS = 10;
    private final ThingRegistry thingRegistry;
    private final VicareConfiguration config;

    private final VicareService vicareService;
    private final Map<String, CachedResponse> cachedResponses = new HashMap<>();
    private String bindingVersion;

    private static class CachedResponse {
        final CompletableFuture<List<Feature>> response;
        final Instant responseTimestamp;

        public CachedResponse(CompletableFuture<List<Feature>> response, Instant responseTimestamp) {
            this.response = response;
            this.responseTimestamp = responseTimestamp;
        }
    }

    private static final int REQUEST_INTERVAL_SECS = 90;

    private volatile ScheduledFuture<?> featurePollingJob;

    /**
     * @param thingRegistry
     * @param bridge
     * @see BaseThingHandler
     */
    public VicareBridgeHandler(VicareServiceProvider vicareServiceProvider,
                               Bridge bridge) {
        super(bridge);
        this.vicareService = vicareServiceProvider.getVicareService();
        this.thingRegistry = vicareServiceProvider.getThingRegistry();
        this.config = vicareServiceProvider.getVicareConfiguration();
        this.bindingVersion = vicareServiceProvider.getBindingVersion();
        ((SimpleConfiguration)this.config).setConfigurationParameters(getConfig().getProperties());
    }

    @Override
    public void initialize() {
        updateProperty(VicareConstants.PROPERTY_BINDING_VERSION, bindingVersion);
        updateStatus(ThingStatus.UNKNOWN);
        featurePollingJob = scheduler.scheduleAtFixedRate(featurePoller(), POLLING_STARTUP_DELAY_SECS, getPollingInterval(), TimeUnit.SECONDS);
        logger.debug("VicareBridgeHandler initialised");
    }

    @Override
    public void dispose() {
        logger.debug("VicareBridgeHandler disposing");
        if (featurePollingJob != null) {
            featurePollingJob.cancel(false);
        }
        super.dispose();
    }

    private int getPollingInterval() {
        BigDecimal pollingInterval = (BigDecimal) getConfig().getProperties().get("pollingInterval");
        return pollingInterval == null ? REQUEST_INTERVAL_SECS : pollingInterval.intValue();
    }

    @Override
    public void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    private Runnable featurePoller() {
        return () -> {
            for (Thing thing : getThing().getThings()) {
                VicareDeviceThingHandler handler = (VicareDeviceThingHandler) thing.getHandler();
                if (handler != null) {
                    for (Channel channel : thing.getChannels()) {
                        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
                    }
                }
            }
        };
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        ((SimpleConfiguration) config).setConfigurationParameters(configurationParameters);
    }

    public Optional<Feature> handleBridgedDeviceCommand(ChannelUID channelUID, Command command) throws AuthenticationException, IOException, CommandFailureException {
        logger.trace("Handling command {} for channel {} from thing {}", command, channelUID, channelUID.getThingUID());
        Thing targetThing = thingRegistry.get(channelUID.getThingUID());
        Channel channel = targetThing.getChannel(channelUID);
        if (command instanceof RefreshType) {
            return getFeatures(targetThing)
                    .thenApply(features -> {
                        String featureName = channel.getProperties().get(PROPERTY_FEATURE_NAME);
                        if (getThing().getStatus() != ThingStatus.ONLINE) {
                            updateStatus(ThingStatus.ONLINE);
                        }
                        return features.stream()
                                .filter(f -> f.getName().equals(featureName))
                                .findAny();
                               })
                    .exceptionally(e -> {
                        if (e instanceof AuthenticationException) {
                          updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to authenticate with Viessmann API: " + e.getMessage());
                        } else {
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to communicate with Viessmann API: " + e.getMessage());
                        }
                        logger.warn("Unexpected exception refreshing", e);
                        return empty();
                    })
                    .join();
        } else if (command instanceof StringType) {
            sendCommand(channelUID, targetThing, channel, () -> ((StringType) command).toString());
        } else if (command instanceof DecimalType) {
            sendCommand(channelUID, targetThing, channel, () -> ((DecimalType) command).doubleValue());
        } else if (command instanceof QuantityType) {
            sendCommand(channelUID, targetThing, channel, () -> ((QuantityType<?>) command).doubleValue());
        } else {
            logger.trace("Ignored unsupported command type {}", command);
        }
        return empty();
    }

    private void sendCommand(ChannelUID channelUID, Thing targetThing, Channel channel, Supplier<Object> valueSupplier) throws AuthenticationException, IOException, CommandFailureException {
        CommandDescriptor commandDescriptor = getCommandDescriptor(targetThing.getChannel(channelUID)).orElse(null);
        if (commandDescriptor != null) {
            Object value = valueSupplier.get();
            logger.debug("Sending command {} ({})", commandDescriptor.getUri(), value);
            vicareService.sendCommand(commandDescriptor.getUri(), Map.of(channel.getProperties().get(PROPERTY_PARAM_NAME),
                                                                         value));
        }
    }

    private synchronized CompletableFuture<List<Feature>> getFeatures(Thing thing) {
        Instant now = Instant.now();
        String key = thing.getUID().getId();
        CachedResponse response = cachedResponses.get(key);
        if (response != null && now.isBefore(response.responseTimestamp.plusSeconds(getPollingInterval() - 1))) {
            return response.response;
        }

        VicareUtil.IGD s = decodeThingUniqueId(VicareDeviceThingHandler.getDeviceUniqueId(thing));
        CompletableFuture<List<Feature>> features = new CompletableFuture<>();
        cachedResponses.put(key, new CachedResponse(features, now));
        features.completeAsync(() -> {
            try {
                return vicareService.getFeatures(s.installationId, s.gatewaySerial, s.deviceId);
            } catch (AuthenticationException | IOException e) {
                features.completeExceptionally(e);
                return null;
            }
        });
        return features;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(VicareDiscoveryService.class);
    }

    VicareService getVicareService() {
        return vicareService;
    }

    Optional<CommandDescriptor> getCommandDescriptor(Channel channel) {
        String commandName = channel.getProperties().get(PROPERTY_COMMAND_NAME);
        String featureName = channel.getProperties().get(PROPERTY_FEATURE_NAME);
        Thing thing = thingRegistry.get(channel.getUID().getThingUID());
            return getFeatures(thing)
                    .thenApply(features -> features.stream()
                            .filter(f -> f.getName().equals(featureName))
                            .flatMap(f -> f.getCommands().stream())
                            .filter(c -> c.getName().equals(commandName))
                            .findFirst())
                    .exceptionally(e -> {
                        logger.debug("Unable to get command descriptor", e);
                        return empty();
                    }).join();
    }

    boolean isFeatureScanRunning() {
        return !(featurePollingJob.isCancelled() || featurePollingJob.isDone());
    }
}
