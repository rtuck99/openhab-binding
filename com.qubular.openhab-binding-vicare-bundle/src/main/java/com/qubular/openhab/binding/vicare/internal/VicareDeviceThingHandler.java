package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.vicare.*;
import com.qubular.vicare.model.Unit;
import com.qubular.vicare.model.Value;
import com.qubular.vicare.model.values.*;
import com.qubular.vicare.model.values.DimensionalValue;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.features.*;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.*;
import org.openhab.core.types.*;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.qubular.openhab.binding.vicare.internal.DeviceDiscoveryEvent.generateTopic;
import static com.qubular.openhab.binding.vicare.internal.UnitMapping.apiToOpenHab;
import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.decodeThingUniqueId;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.stream.Collectors.toMap;
import static org.osgi.service.event.EventConstants.EVENT_TOPIC;

public class VicareDeviceThingHandler extends BaseThingHandler {
    static final Map<ConsumptionFeature.Stat, String> CONSUMPTION_CHANNEL_NAMES_BY_STAT = Map.of(
            ConsumptionFeature.Stat.CURRENT_DAY, "currentDay",
            ConsumptionFeature.Stat.CURRENT_WEEK, "currentWeek",
            ConsumptionFeature.Stat.CURRENT_MONTH, "currentMonth",
            ConsumptionFeature.Stat.CURRENT_YEAR, "currentYear",
            ConsumptionFeature.Stat.LAST_SEVEN_DAYS, "lastSevenDays",
            ConsumptionFeature.Stat.PREVIOUS_DAY, "previousDay",
            ConsumptionFeature.Stat.PREVIOUS_WEEK, "previousWeek",
            ConsumptionFeature.Stat.PREVIOUS_MONTH, "previousMonth",
            ConsumptionFeature.Stat.PREVIOUS_YEAR, "previousYear"
    );
    private static final Set<String> DEVICE_PROPERTIES = Set.of(
            PROPERTY_DEVICE_TYPE,
            PROPERTY_GATEWAY_SERIAL,
            PROPERTY_DEVICE_UNIQUE_ID,
            PROPERTY_MODEL_ID,
            PROPERTY_BOILER_SERIAL
    );

    static final Map<String, String> COMMAND_NAMES_TO_PROPS = Map.of(
            "activate", PROPERTY_ON_COMMAND_NAME,
            "deactivate", PROPERTY_OFF_COMMAND_NAME
    );

    private static final Logger logger = LoggerFactory.getLogger(VicareDeviceThingHandler.class);
    private final VicareService vicareService;
    private final VicareServiceProvider vicareServiceProvider;
    private ServiceRegistration<EventHandler> discoveryListenerRegistration;

    private static final Map<String, ConsumptionFeature.Stat> CONSUMPTION_STATS_BY_CHANNEL_NAME =
            CONSUMPTION_CHANNEL_NAMES_BY_STAT.entrySet().stream()
                    .collect(toMap(Map.Entry::getValue, Map.Entry::getKey));

    public VicareDeviceThingHandler(VicareServiceProvider vicareServiceProvider,
                                    Thing thing,
                                    VicareService vicareService) {
        super(thing);
        this.vicareServiceProvider = vicareServiceProvider;
        logger.info("Creating handler for {}", thing.getUID());
        this.vicareService = vicareService;
    }

    @Override
    public void dispose() {
        if (discoveryListenerRegistration != null) {
            discoveryListenerRegistration.unregister();
            discoveryListenerRegistration = null;
        }
        super.dispose();
    }

    /**
     * Create the channels
     */
    @Override
    public void initialize() {
        String deviceUniqueId = VicareUtil.getDeviceUniqueId(thing);
        VicareUtil.IGD igd = decodeThingUniqueId(deviceUniqueId);
        Hashtable<String, Object> subscriptionProps = new Hashtable<>();
        subscriptionProps.put(EVENT_TOPIC, generateTopic(thing.getUID()));
        discoveryListenerRegistration = vicareServiceProvider.getBundleContext().registerService(
                EventHandler.class, new DiscoveryEventHandler(), subscriptionProps);
        CompletableFuture.supplyAsync(new VicareChannelBuilder(vicareServiceProvider, igd, thing, this::createChannelBuilder, vicareServiceProvider.getChannelTypeProvider()::addChannelType))
                .thenAccept(memo -> {
                    try {
                        VicareChannelBuilder.Result result = memo.get();
                        if (!result.channels.isEmpty() || !result.newPropValues.isEmpty()) {
                            ThingBuilder thingBuilder = editThing();
                            if (!result.newPropValues.isEmpty()) {
                                thingBuilder = thingBuilder.withProperties(result.newPropValues);
                            }

                            if (!result.channels.isEmpty()) {
                                var sortedChannels = result.channels.stream()
                                        .sorted(Comparator.comparing(c -> c.getUID().getId()))
                                        .collect(Collectors.toList());
                                thingBuilder = thingBuilder.withChannels(sortedChannels);
                            }
                            updateThing(thingBuilder.build());
                        }
                        updateStatus(ThingStatus.ONLINE);
                    } catch (AuthenticationException e) {
                        logger.warn("Unable to authenticate while fetching device features", e);
                        updateStatus(ThingStatus.OFFLINE,
                                     ThingStatusDetail.COMMUNICATION_ERROR,
                                     "Authentication problem fetching device features: " + e.getMessage());
                    } catch (IOException e) {
                        logger.warn("IOException while fetching device features", e);
                        updateStatus(ThingStatus.OFFLINE,
                                     ThingStatusDetail.COMMUNICATION_ERROR,
                                     "Communication problem fetching device features: " + e.getMessage());
                    }
                })
                .exceptionally(t -> { logger.warn("Unexpected error initializing Thing", t); return null; });
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        CompletableFuture.runAsync(() -> {
                    try {
                        syncHandleCommand(channelUID, command);
                    } catch (AuthenticationException e) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Unable to authenticate with Viessmann API: " + e.getMessage());
                    } catch (VicareServiceException e) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to communicate with device: " + e.getMessage());
                    } catch (IOException e) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Unable to communicate with Viessmann API: " + e.getMessage());
                    } catch (CommandFailureException e) {
                        logger.warn("Unable to perform command {} for channel {} {}: {}", command, channelUID, e.getReason(), e.getMessage());
                    }
                })
                .exceptionally(t -> {
                    // reduce amount of log spam for failed REFRESH
                    if (command != RefreshType.REFRESH) {
                        logger.warn(format("Unexpected exception handling command %s for channel %s", command, channelUID), t);
                    } else {
                        logger.debug(format("Unexpected exception handling command %s for channel %s", command, channelUID), t);
                    }
                    return null;
                });
    }

    private void syncHandleCommand(ChannelUID channelUID, Command command) throws AuthenticationException, IOException, CommandFailureException {
        if (command == RefreshType.REFRESH) {
            syncHandleRefreshCommand(channelUID);
        } else if (command instanceof State state){
            syncHandleOtherCommand(channelUID, state);
        }
    }

    private void syncHandleOtherCommand(ChannelUID channelUID, State command) throws AuthenticationException, IOException, CommandFailureException {
        Optional<State> newValue = getBridgeHandler().handleBridgedDeviceCommand(channelUID, command);
        newValue.ifPresent(state -> updateState(channelUID, state));
    }

    private void syncHandleRefreshCommand(ChannelUID channelUID) throws AuthenticationException, IOException {
        Optional<Feature> feature = ((VicareBridgeHandler) getBridge().getHandler()).handleBridgedRefreshCommand(
                channelUID);
        feature.ifPresent(f -> {
            f.accept(new Feature.Visitor() {
                @Override
                public void visit(ConsumptionFeature f) {
                    Channel channel = getThing().getChannel(channelUID);
                    String statName = channel.getProperties().get(PROPERTY_PROP_NAME);
                    Optional<DimensionalValue> stat = f.getConsumption(
                            CONSUMPTION_STATS_BY_CHANNEL_NAME.get(statName));
                    updateConsumptionStat(stat.map(DimensionalValue::getValue).orElse(0.0),
                                          stat.map(DimensionalValue::getUnit).orElse(null));
                }

                private void updateConsumptionStat(Double value, Unit unit) {
                    updateState(channelUID, apiToOpenHab(unit, value));
                }

                @Override
                public void visit(NumericSensorFeature f) {
                    Channel channel = getThing().getChannel(channelUID);
                    String propName = channel.getProperties().get(PROPERTY_PROP_NAME);
                    if ("active".equals(propName)) {
                        updateState(channelUID, f.isActive() ? OnOffType.ON : OnOffType.OFF);
                    } else if ("status".equals(propName)) {
                        updateState(channelUID, StringType.valueOf(f.getStatus() == null ? null : f.getStatus().getName()));
                    } else {
                        Value v = f.getProperties().get(propName);
                        if (v instanceof DimensionalValue) {
                            double value = ((DimensionalValue) v).getValue();
                            updateState(channelUID, new DecimalType(value));
                        }
                    }
                }

                @Override
                public void visit(StatusSensorFeature f) {
                    Channel channel = getThing().getChannel(channelUID);
                    String propertyName = channel.getProperties().get(PROPERTY_PROP_NAME);
                    State state;
                    switch (propertyName) {
                        case "status":
                            state = StringType.valueOf(f.getStatus() == null ? null : f.getStatus().getName());
                            break;
                        default:
                            Value value = f.getProperties().get(propertyName);
                            var visitor = new Value.Visitor() {
                                State state = UnDefType.UNDEF;

                                @Override
                                public void visit(ArrayValue v) {
                                    unsupportedValue(v);
                                }

                                @Override
                                public void visit(BooleanValue v) {
                                    state = v.getValue() ? OnOffType.ON : OnOffType.OFF;
                                }

                                @Override
                                public void visit(DimensionalValue v) {
                                    state = new DecimalType(v.getValue());
                                }

                                @Override
                                public void visit(LocalDateValue v) {
                                    unsupportedValue(v);
                                }

                                @Override
                                public void visit(StatusValue v) {
                                    unsupportedValue(v);
                                }

                                @Override
                                public void visit(StringValue v) {
                                    state = new StringType(v.getValue());
                                }

                                private void unsupportedValue(Value v) {
                                    logger.trace("Unable to update unsupported value {} for property {}.{}",
                                            v, f.getName(), propertyName);
                                }
                            };
                            value.accept(visitor);
                            state = visitor.state;
                            break;
                    }
                    updateState(channelUID, state);
                }

                @Override
                public void visit(TextFeature f) {
                    logger.trace("Update {} with {}", channelUID, f.getValue());
                    updateState(channelUID, new StringType(f.getValue()));
                }

                @Override
                public void visit(CurveFeature f) {
                    Channel channel = getThing().getChannel(channelUID);
                    switch (channel.getProperties().get(PROPERTY_PROP_NAME)) {
                        case "slope":
                            State slopeState = new DecimalType(f.getSlope().getValue());
                            updateState(channelUID, slopeState);
                            break;
                        case "shift":
                            State shiftState = new DecimalType(f.getShift().getValue());
                            updateState(channelUID, shiftState);
                            break;
                    }
                }

                @Override
                public void visit(DatePeriodFeature datePeriodFeature) {
                    Channel channel = getThing().getChannel(channelUID);
                    State newState = UnDefType.UNDEF;
                    switch (channel.getProperties().get(PROPERTY_PROP_NAME)) {
                        case "active":
                            newState = StatusValue.ON.equals(datePeriodFeature.getActive()) ? OnOffType.ON : OnOffType.OFF;
                            break;
                        case "start":
                            LocalDate startDate = datePeriodFeature.getStart();
                            if (startDate != null) {
                                newState = new DateTimeType(startDate.atStartOfDay(ZoneId.systemDefault()));
                            }
                            break;
                        case "end":
                            LocalDate endDate = datePeriodFeature.getEnd();
                            if (endDate != null) {
                                newState = new DateTimeType(endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()));
                            }
                            break;
                    }
                    updateState(channelUID, newState);
                }
            });
        });
        if (thing.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(DeviceDynamicCommandDescriptionProvider.class);
    }

    protected @Nullable VicareBridgeHandler getBridgeHandler() {
        return (VicareBridgeHandler) getBridge().getHandler();
    }

    private class DiscoveryEventHandler implements EventHandler {
        @Override
        public void handleEvent(Event event) {
            Map<String, String> oldProps = editProperties();

            Set<String> propsToWipe = new HashSet<>(oldProps.keySet());
            propsToWipe.removeAll(DEVICE_PROPERTIES);
            propsToWipe.forEach(key -> oldProps.put(key, null));

            Arrays.stream(event.getPropertyNames()).forEach(name -> {
                if (!EVENT_TOPIC.equals(name)) {
                    Object value = event.getProperty(name);
                    if (value instanceof String) {
                        oldProps.put(name, (String) value);
                    }
                }
            });

            updateProperties(oldProps);
        }
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        super.updateStatus(status, statusDetail, description);
    }

    @Override
    public void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    ChannelBuilder createChannelBuilder(ChannelUID channelUID, ChannelTypeUID channelTypeUID) {
        return getCallback().createChannelBuilder(channelUID, channelTypeUID);
    }
}
