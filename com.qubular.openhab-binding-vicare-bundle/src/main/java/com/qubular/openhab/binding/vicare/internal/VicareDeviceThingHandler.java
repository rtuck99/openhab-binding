package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.DimensionalValue;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Status;
import com.qubular.vicare.model.features.*;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.decodeThingUniqueId;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.escapeUIDSegment;
import static com.qubular.vicare.model.Status.OFF;
import static com.qubular.vicare.model.Status.ON;

public class VicareDeviceThingHandler extends BaseThingHandler {
    private static final Logger logger = LoggerFactory.getLogger(VicareDeviceThingHandler.class);
    private final VicareService vicareService;

    private static final Map<String, Function<ConsumptionFeature, DimensionalValue>> consumptionAccessorMap = Map.of(
            "currentDay", ConsumptionFeature::getToday,
            "lastSevenDays", ConsumptionFeature::getSevenDays,
            "currentMonth", ConsumptionFeature::getMonth,
            "currentYear", ConsumptionFeature::getYear
    );

    public VicareDeviceThingHandler(Thing thing, VicareService vicareService) {
        super(thing);
        logger.info("Creating handler for {}", thing.getUID());
        this.vicareService = vicareService;
    }

    /**
     * Create the channels
     */
    @Override
    public void initialize() {
        String deviceUniqueId = thing.getProperties().get(PROPERTY_DEVICE_UNIQUE_ID);
        VicareUtil.IGD igd = decodeThingUniqueId(deviceUniqueId);
        CompletableFuture.runAsync(() -> {
            try {
                List<Feature> features = vicareService.getFeatures(igd.installationId, igd.gatewaySerial, igd.deviceId);
                List<Channel> channels = new ArrayList<>();
                Map<String, String> newPropValues = new HashMap<>(getThing().getProperties());
                for (Feature feature : features) {
                    feature.accept(new Feature.Visitor() {
                        @Override
                        public void visit(ConsumptionFeature f) {
                            String id = escapeUIDSegment(f.getName());
                            channels.add(consumptionChannel(id, f, "currentDay"));
                            channels.add(consumptionChannel(id, f, "lastSevenDays"));
                            channels.add(consumptionChannel(id, f, "currentMonth"));
                            channels.add(consumptionChannel(id, f, "currentYear"));
                        }

                        private Channel consumptionChannel(String id, Feature feature, String statName) {
                            return ChannelBuilder.create(new ChannelUID(thing.getUID(), id + "_" + statName))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(id + "_" + statName)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                            PROPERTY_STATISTIC_NAME, statName))
                                    .build();
                        }

                        @Override
                        public void visit(NumericSensorFeature f) {
                            String id = escapeUIDSegment(f.getName());
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), id))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(id)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName()))
                                    .build());
                            if (f.getStatus() != null && !Status.NA.equals(f.getStatus())) {
                                String activeId = id + "_active";
                                channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), activeId))
                                        .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(activeId)))
                                        .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName()))
                                        .build());
                            }
                        }

                        @Override
                        public void visit(StatisticsFeature f) {
                            f.getStatistics().forEach((name, value) -> {
                                String id = escapeUIDSegment(f.getName() + "_" + name);
                                channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), id))
                                        .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(id)))
                                        .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                                PROPERTY_STATISTIC_NAME, name))
                                        .build());
                            });
                        }

                        @Override
                        public void visit(StatusSensorFeature f) {
                            String id = escapeUIDSegment(f.getName());
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), id))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(id)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, f.getName()))
                                    .build());
                        }

                        @Override
                        public void visit(TextFeature f) {
                            String name = f.getName();
                            newPropValues.put(name, f.getValue());
                        }

                        @Override
                        public void visit(CurveFeature f) {
                            String slopeId = escapeUIDSegment(f.getName() + "_slope");
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), slopeId))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(slopeId)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, f.getName(),
                                            PROPERTY_STATISTIC_NAME, "slope"))
                                    .build());
                            String shiftId = escapeUIDSegment(f.getName() + "_shift");
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), shiftId))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(shiftId)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, f.getName(),
                                            PROPERTY_STATISTIC_NAME, "shift"))
                                    .build());
                        }

                        @Override
                        public void visit(DatePeriodFeature datePeriodFeature) {
                            String activeId = escapeUIDSegment(feature.getName() + "_active");
                            String startId = escapeUIDSegment(feature.getName() + "_start");
                            String endId = escapeUIDSegment(feature.getName() + "_end");
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), activeId))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(activeId)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                            PROPERTY_STATISTIC_NAME, "active"))
                                    .build());
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), startId))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(startId)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                            PROPERTY_STATISTIC_NAME, "start"))
                                    .build());
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), endId))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(endId)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                            PROPERTY_STATISTIC_NAME, "end"))
                                    .build());
                        }
                    });

                }
                if (!channels.isEmpty() || !newPropValues.isEmpty()) {
                    ThingBuilder thingBuilder = editThing();
                    if (!newPropValues.isEmpty()) {
                        thingBuilder = thingBuilder.withProperties(newPropValues);
                    }
                    if (!channels.isEmpty()) {
                        thingBuilder = thingBuilder.withChannels(channels);
                    }
                    updateThing(thingBuilder.build());
                }
                updateStatus(ThingStatus.ONLINE);
            } catch (AuthenticationException e) {
                updateStatus(ThingStatus.UNINITIALIZED,
                        ThingStatusDetail.COMMUNICATION_ERROR,
                        "Authentication problem fetching device features: " + e.getMessage());
                logger.warn("Unable to authenticate while fetching device features", e);
            } catch (IOException e) {
                updateStatus(ThingStatus.UNINITIALIZED,
                        ThingStatusDetail.COMMUNICATION_ERROR,
                        "Communication problem fetching device features: " + e.getMessage());
                logger.warn("IOException while fetching device features", e);
            }

        }).exceptionally(t -> { logger.warn("Unexpected error initializing Thing", t); return null; });
    }

    private String channelIdToChannelType(String channelId) {
        return channelId.replaceAll("(_[\\d+])(_?)", "$2");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            Optional<Feature> feature = ((VicareBridgeHandler) getBridge().getHandler()).handleBridgedDeviceCommand(channelUID, command);
            feature.ifPresent(f -> {
                f.accept(new Feature.Visitor() {
                    @Override
                    public void visit(ConsumptionFeature f) {
                        Channel channel = getThing().getChannel(channelUID);
                        String statName = channel.getProperties().get(PROPERTY_STATISTIC_NAME);
                        updateConsumptionStat(() -> consumptionAccessorMap.get(statName).apply(f).getValue());
                    }

                    private void updateConsumptionStat(Supplier<Double> valueSupplier) {
                        updateState(channelUID, new DecimalType(valueSupplier.get()));
                    }

                    @Override
                    public void visit(NumericSensorFeature f) {
                        double value = f.getValue().getValue();
                        Channel channel = getThing().getChannel(channelUID);
                        ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
                        if (channelTypeUID.getId().endsWith("_active")) {
                            updateState(channelUID, Status.ON.equals(f.getStatus()) ? OnOffType.ON : OnOffType.OFF);
                        } else {
                            updateState(channelUID, new DecimalType(value));
                        }
                    }

                    @Override
                    public void visit(StatisticsFeature f) {
                        Channel channel = getThing().getChannel(channelUID);
                        String statisticName = channel.getProperties().get(PROPERTY_STATISTIC_NAME);
                        double value = f.getStatistics().get(statisticName).getValue();
                        updateState(channelUID, new DecimalType(value));
                    }

                    @Override
                    public void visit(StatusSensorFeature f) {
                        State state = null;
                        if (ON.equals(f.getStatus())) {
                            state = OnOffType.ON;
                        } else if (OFF.equals(f.getStatus())) {
                            state = OnOffType.OFF;
                        } else {
                            logger.debug("Unable to map state {} for {}", f.getStatus(), f.getName());
                        }
                        updateState(channelUID, state);
                    }

                    @Override
                    public void visit(TextFeature f) {

                    }

                    @Override
                    public void visit(CurveFeature f) {
                        Channel channel = getThing().getChannel(channelUID);
                        switch (channel.getProperties().get(PROPERTY_STATISTIC_NAME)) {
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
                        switch (channel.getProperties().get(PROPERTY_STATISTIC_NAME)) {
                            case "active":
                                OnOffType onOffType = Status.ON.equals(datePeriodFeature.getActive()) ? OnOffType.ON : OnOffType.OFF;
                                updateState(channelUID, onOffType);
                                break;
                            case "start":
                                DateTimeType start = new DateTimeType(datePeriodFeature.getStart().atStartOfDay(ZoneId.systemDefault()));
                                updateState(channelUID, start);
                                break;
                            case "end":
                                DateTimeType end = new DateTimeType(datePeriodFeature.getEnd().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()));
                                updateState(channelUID, end);
                                break;
                        }
                    }
                });
            });
            if (thing.getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (AuthenticationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Unable to authenticate with Viessmann API: " + e.getMessage());
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Unable to communicate with Viessmann API: " + e.getMessage());
        }
    }
}
