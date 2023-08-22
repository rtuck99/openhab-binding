package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.configuration.SimpleConfiguration;
import com.qubular.openhab.binding.vicare.internal.tokenstore.PersistedTokenStore;
import com.qubular.vicare.*;
import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.type.AutoUpdatePolicy;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.osgi.service.cm.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.empty;

public class VicareBridgeHandler extends BaseBridgeHandler implements VicareThingHandler {
    public static final String CONFIG_USE_LIMITED_ENCRYPTION = "useLimitedEncryption";
    public static final String CONFIG_POLLING_INTERVAL = "pollingInterval";
    private static final Logger logger = LoggerFactory.getLogger(VicareBridgeHandler.class);
    public static final int POLLING_STARTUP_DELAY_SECS = 10;
    private final ThingRegistry thingRegistry;
    private final VicareConfiguration config;

    private final VicareService vicareService;
    private String bindingVersion;
    private final VicareServiceProvider vicareServiceProvider;

    public static final int DEFAULT_POLLING_INTERVAL = 90;

    private volatile ScheduledFuture<?> featurePollingJob;

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
        upgradeConfiguration();
        updateProperty(VicareConstants.PROPERTY_BINDING_VERSION, bindingVersion);
        updateProperty(PROPERTY_RESPONSE_CAPTURE_FOLDER, config.getResponseCaptureFolder() != null ? config.getResponseCaptureFolder().getAbsolutePath().toString() : "");
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
        BigDecimal pollingInterval = (BigDecimal) getConfig().getProperties().get(CONFIG_POLLING_INTERVAL);
        return pollingInterval == null ? DEFAULT_POLLING_INTERVAL : pollingInterval.intValue();
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
            record HandlerChannel(ThingHandler handler, Channel channel){}

            getThing().getThings().stream()
                    .map(Thing::getHandler)
                    .filter(Objects::nonNull)
                    .map(VicareDeviceThingHandler.class::cast)
                    .forEach(handler -> {
                        // prime the feature cache
                        logger.debug("Prefetching features for {}", handler.getThing());
                        vicareServiceProvider.getFeatureService().getFeatures(handler.getThing(), getPollingInterval())
                                .exceptionally(ex -> {
                                    logger.warn("Unable to prefetch features", ex);
                                    return emptyList();
                                })
                                .thenRun(() -> {
                                    handler.getThing().getChannels().stream().map(c -> new HandlerChannel(handler, c))
                                            .forEach(handlerChannel -> handler.handleCommand(handlerChannel.channel.getUID(), RefreshType.REFRESH));
                                         }
                                );
                    });
        };
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        applyConfiguration(configurationParameters);
        vicareServiceProvider.getFeatureService().clear();
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

    public Optional<Feature> handleBridgedRefreshCommand(ChannelUID channelUID) throws AuthenticationException, IOException {
        logger.trace("Handling REFRESH for channel {} from thing {}", channelUID, channelUID.getThingUID());
        Thing targetThing = thingRegistry.get(channelUID.getThingUID());
        Channel channel = targetThing.getChannel(channelUID);
        if (! channel.getProperties().containsKey(PROPERTY_PROP_NAME)) {
            // Don't refresh channels that represent commands
            return empty();
        }
        try {
            String featureName = channel.getProperties().get(PROPERTY_FEATURE_NAME);
            return vicareServiceProvider.getFeatureService().getFeature(targetThing, featureName, getPollingInterval())
                .thenApply(feature -> {
                    if (getThing().getStatus() != ThingStatus.ONLINE) {
                        updateStatus(ThingStatus.ONLINE);
                    }
                    return feature;
                })
                .join();
        } catch (CompletionException e) {
            Throwable t = e.getCause();
            if (t instanceof AuthenticationException ae) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                             "Unable to authenticate with Viessmann API: " + e.getMessage());
                throw ae;
            } else if (!(t instanceof VicareServiceException)) {
                    // VicareServiceException handled by device
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                     "Unable to communicate with Viessmann API: " + e.getMessage());
                        logger.debug("Unexpected exception refreshing", e);
            }
            if (t instanceof IOException ioe) {
                throw ioe;
            }
            return empty();
        }
    }

    public Optional<State> handleBridgedDeviceCommand(ChannelUID channelUID, State command) throws AuthenticationException, IOException, CommandFailureException {
        logger.trace("Handling command {} for channel {} from thing {}", command, channelUID, channelUID.getThingUID());
        Thing targetThing = thingRegistry.get(channelUID.getThingUID());
        Channel channel = targetThing.getChannel(channelUID);
        if (command instanceof StringType) {
            sendCommand(channelUID, targetThing, channel, () -> ((StringType) command).toString());
        } else if (command instanceof DecimalType) {
            sendCommand(channelUID, targetThing, channel, () -> ((DecimalType) command).doubleValue());
        } else if (command instanceof QuantityType) {
            sendCommand(channelUID, targetThing, channel, () -> ((QuantityType<?>) command).doubleValue());
        } else if (command instanceof OnOffType) {
            sendCommand(channelUID, targetThing, channel, () -> OnOffType.ON.equals(command));
        } else {
            logger.trace("Ignored unsupported command type {}", command);
            return empty();
        }
        ChannelType channelType = vicareServiceProvider.getChannelTypeRegistry().getChannelType(channel.getChannelTypeUID());
        if (channelType.getAutoUpdatePolicy() == AutoUpdatePolicy.VETO) {
            return Optional.of(command);
        }
        return empty();
    }

    private void sendCommand(ChannelUID channelUID, Thing targetThing, Channel channel, Supplier<Object> valueSupplier) throws AuthenticationException, IOException, CommandFailureException {
        Object value = valueSupplier.get();
        CommandDescriptor commandDescriptor = getCommandDescriptor(targetThing.getChannel(channelUID), value).orElse(null);
        if (commandDescriptor != null) {
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

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(VicareDiscoveryService.class);
    }

    @Override
    public VicareService getVicareService() {
        return vicareService;
    }

    Optional<CommandDescriptor> getCommandDescriptor(Channel channel, @Nullable Object value) {
        String commandName;
        String featureName = channel.getProperties().get(PROPERTY_FEATURE_NAME);
        if (channel.getProperties().containsKey(PROPERTY_COMMAND_NAME)) {
            commandName = channel.getProperties().get(PROPERTY_COMMAND_NAME);
        } else if (Boolean.TRUE.equals(value) && channel.getProperties().containsKey(PROPERTY_ON_COMMAND_NAME)) {
            commandName = channel.getProperties().get(PROPERTY_ON_COMMAND_NAME);
        } else if (Boolean.FALSE.equals(value) && channel.getProperties().containsKey(PROPERTY_OFF_COMMAND_NAME)) {
            commandName = channel.getProperties().get(PROPERTY_OFF_COMMAND_NAME);
        } else {
            logger.debug("No matching command for channel {} value {}", channel.getUID(), value);
            return empty();
        }
        Thing thing = thingRegistry.get(channel.getUID().getThingUID());
            return vicareServiceProvider.getFeatureService().getFeature(thing, featureName, getPollingInterval())
                    .thenApply(feature -> feature.stream()
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

    private void upgradeConfiguration() {
        org.openhab.core.config.core.Configuration config = editConfiguration();
        config.setProperties(SimpleConfiguration.upgradeConfiguration(config.getProperties()));
        updateConfiguration(config);
    }
}
