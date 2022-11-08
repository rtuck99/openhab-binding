package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.configuration.SimpleConfiguration;
import com.qubular.openhab.binding.vicare.internal.tokenstore.PersistedTokenStore;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.CommandFailureException;
import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Value;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.osgi.service.cm.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.decodeThingUniqueId;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.empty;

public class VicareBridgeHandler extends BaseBridgeHandler {
    public static final String CONFIG_USE_LIMITED_ENCRYPTION = "useLimitedEncryption";
    private static final Logger logger = LoggerFactory.getLogger(VicareBridgeHandler.class);
    public static final int POLLING_STARTUP_DELAY_SECS = 10;
    private final ThingRegistry thingRegistry;
    private final VicareConfiguration config;

    private final VicareService vicareService;
    private final Map<String, CachedResponse> cachedResponses = new HashMap<>();
    private String bindingVersion;
    private final VicareServiceProvider vicareServiceProvider;

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
        this.vicareServiceProvider = vicareServiceProvider;
        applyConfiguration(getConfig().getProperties());
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
        applyConfiguration(configurationParameters);
        cachedResponses.clear();
    }

    private void applyConfiguration(Map<String, Object> configurationParameters) {
        ((SimpleConfiguration) config).setConfigurationParameters(configurationParameters);
        try {
            Configuration configuration = vicareServiceProvider.getConfigurationAdmin().getConfiguration(
                    PersistedTokenStore.TOKEN_STORE_PID);
            Dictionary<String, Object> props = requireNonNullElseGet(configuration.getProperties(), Hashtable::new);
            boolean useLimitedEncryption = requireNonNullElse((Boolean) configurationParameters.get(CONFIG_USE_LIMITED_ENCRYPTION), false);
            props.put(CryptUtil.CONFIG_USE_LIMITED_ENCRYPTION, useLimitedEncryption);
            configuration.update(props);
        } catch (IOException e) {
            logger.warn("Unable to write PersistedTokenStore configuration", e);
        }
    }

    public Optional<Feature> handleBridgedDeviceCommand(ChannelUID channelUID, Command command) throws AuthenticationException, IOException, CommandFailureException {
        logger.trace("Handling command {} for channel {} from thing {}", command, channelUID, channelUID.getThingUID());
        Thing targetThing = thingRegistry.get(channelUID.getThingUID());
        Channel channel = targetThing.getChannel(channelUID);
        if (command instanceof RefreshType) {
            if (! channel.getProperties().containsKey(PROPERTY_PROP_NAME)) {
                // Don't refresh channels that represent commands
                return empty();
            }
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
        } else if (command instanceof OnOffType) {
            sendCommand(channelUID, targetThing, channel, () -> OnOffType.ON.equals(command));
        } else {
            {
                logger.trace("Ignored unsupported command type {}", command);
            }
        }
        return empty();
    }

    private void sendCommand(ChannelUID channelUID, Thing targetThing, Channel channel, Supplier<Object> valueSupplier) throws AuthenticationException, IOException, CommandFailureException {
        CommandDescriptor commandDescriptor = getCommandDescriptor(targetThing.getChannel(channelUID)).orElse(null);
        if (commandDescriptor != null) {
            Object value = valueSupplier.get();
            logger.debug("Sending command {} ({})", commandDescriptor.getUri(), value);
            Map<String, Object> values = new HashMap<>();
            String paramName = channel.getProperties().get(PROPERTY_PARAM_NAME);
            if (paramName != null) {
                values.put(paramName, value);
                vicareService.sendCommand(commandDescriptor.getUri(), values);
            } else if (Boolean.TRUE.equals(value)) {
                vicareService.sendCommand(commandDescriptor.getUri(), emptyMap());
            }
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
                if ((e instanceof AuthenticationException) &&
                        (e.getCause() instanceof InvalidKeyException)) {
                    features.completeExceptionally(new AuthenticationException("Unable to store access token, please check whether your crypto.policy is set to enable full strength encryption or enable limited encryption in Advanced Settings.", (Exception) e.getCause()));
                } else {
                    features.completeExceptionally(e);
                }
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
