package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.features.*;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.decodeThingUniqueId;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.escapeUIDSegment;

public class VicareDeviceThingHandler extends BaseThingHandler {
    private static final Logger logger = LoggerFactory.getLogger(VicareDeviceThingHandler.class);
    private final VicareService vicareService;

    public VicareDeviceThingHandler(Thing thing, VicareService vicareService) {
        super(thing);
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
                for (Feature feature : features) {
                    feature.accept(new Feature.Visitor() {
                        @Override
                        public void visit(ConsumptionFeature f) {

                        }

                        @Override
                        public void visit(NumericSensorFeature f) {
                            String id = escapeUIDSegment(feature.getName());
                            channels.add(ChannelBuilder.create(new ChannelUID(thing.getUID(), id))
                                    .withType(new ChannelTypeUID(BINDING_ID, channelIdToChannelType(id)))
                                    .withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName()))
                                    .build());
                        }

                        @Override
                        public void visit(StatisticsFeature f) {

                        }

                        @Override
                        public void visit(StatusSensorFeature f) {

                        }

                        @Override
                        public void visit(TextFeature f) {

                        }
                    });
                    if (!channels.isEmpty()) {
                        updateThing(editThing().withChannels(channels).build());
                    }
                }
            } catch (AuthenticationException e) {
                thing.setStatusInfo(new ThingStatusInfo(ThingStatus.UNINITIALIZED,
                        ThingStatusDetail.COMMUNICATION_ERROR,
                        "Authentication problem fetching device features: " + e.getMessage()));
                logger.warn("Unable to authenticate while fetching device features", e);
            } catch (IOException e) {
                thing.setStatusInfo(new ThingStatusInfo(ThingStatus.UNINITIALIZED,
                        ThingStatusDetail.COMMUNICATION_ERROR,
                        "Communication problem fetching device features: " + e.getMessage()));
                logger.warn("IOException while fetching device features", e);
            }

        }).exceptionally(t -> { logger.warn("Unexpected error initializing Thing", t); return null; });
    }

    private String channelIdToChannelType(String channelId) {
        return channelId.replaceAll("(_[\\d+])_", "_");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            Optional<Feature> feature = ((VicareBridgeHandler) getBridge().getHandler()).handleBridgedDeviceCommand(channelUID, command);
            feature.ifPresent(f -> {
                f.accept(new Feature.Visitor() {
                    @Override
                    public void visit(ConsumptionFeature f) {

                    }

                    @Override
                    public void visit(NumericSensorFeature f) {
                        double value = f.getValue().getValue();
                        updateState(channelUID, new DecimalType(value));
                    }

                    @Override
                    public void visit(StatisticsFeature f) {

                    }

                    @Override
                    public void visit(StatusSensorFeature f) {

                    }

                    @Override
                    public void visit(TextFeature f) {

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
