package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.*;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.ModifiablePersistenceService;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.*;
import static com.qubular.glowmarkt.AggregationPeriod.*;
import static java.time.Duration.ofDays;
import static java.util.Optional.of;
import static java.util.stream.StreamSupport.stream;

public class GlowmarktVirtualEntityHandler extends BaseThingHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlowmarktVirtualEntityHandler.class);
    private final GlowmarktService glowmarktService;
    private final ItemChannelLinkRegistry itemChannelLinkRegistry;
    private final GlowmarktServiceProvider serviceProvider;
    private TariffChannelTypeProvider tariffChannelTypeProvider;

    public GlowmarktVirtualEntityHandler(GlowmarktServiceProvider serviceProvider, Thing thing, GlowmarktService glowmarktService) {
        super(thing);
        this.glowmarktService = glowmarktService;
        this.itemChannelLinkRegistry = serviceProvider.getItemChannelLinkRegistry();
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void initialize() {
        CompletableFuture.runAsync(() -> {
            logger.info("Initializing virtual entity {}", getThing().getUID());
            GlowmarktBridgeHandler bridgeHandler = (GlowmarktBridgeHandler) getBridge().getHandler();
            String virtualEntityId = getThing().getProperties().get(PROPERTY_VIRTUAL_ENTITY_ID);
            try {
                GlowmarktSession glowmarktSession = bridgeHandler.getGlowmarktSession();
                VirtualEntity virtualEntity = glowmarktService.getVirtualEntity(glowmarktSession, bridgeHandler.getGlowmarktSettings(), virtualEntityId);
                List<Channel> channels = new ArrayList<>();
                for (Resource resource : virtualEntity.getResources()) {
                    ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, channelType(resource));
                    Channel channel = getCallback().createChannelBuilder(new ChannelUID(getThing().getUID(), channelId(resource)), channelTypeUID)
                            .withType(channelTypeUID)
                            .withProperties(Map.of(GlowmarktConstants.PROPERTY_CLASSIFIER, resource.getClassifier(),
                                    GlowmarktConstants.PROPERTY_RESOURCE_ID, resource.getResourceId()))
                            .build();
                    channels.add(channel);

                    if (resource.isConsumption()) {
                    TariffResponse tariffResponse = glowmarktService.getResourceTariff(glowmarktSession,
                                                                                         getBridgeHandler().getGlowmarktSettings(),
                                                                                         resource.getResourceId());
                    List<TariffData> resourceTariff = tariffResponse.getData();
                    var now = LocalDateTime.now();
                    Optional<TariffData> currentTariff = getEffectiveTariff(resourceTariff, now);
                    currentTariff.ifPresent(td -> td.getStructure().forEach(
                            ts -> {
                                ts.getPlanDetails().forEach(pd -> {
                                    if (pd instanceof StandingChargeTariffPlanDetail) {
                                        String channelId = TariffChannelTypeProvider.channelId(TariffChannelTypeProvider.PREFIX_TARIFF_STANDING_CHARGE, resource, ts, pd);
                                        ChannelTypeUID planChannelType = new ChannelTypeUID(BINDING_ID, channelId);
                                        if (tariffChannelTypeProvider != null) {
                                            tariffChannelTypeProvider.createChannelType(planChannelType, Locale.getDefault(), resource.getName(), null);
                                        }
                                        Channel planChannel = getCallback().createChannelBuilder(new ChannelUID(getThing().getUID(), channelId),
                                                                                                 planChannelType)
                                                .withProperties(Map.of(PROPERTY_RESOURCE_ID, resource.getResourceId(),
                                                                       PROPERTY_RESOURCE_NAME, tariffResponse.getName(),
                                                                       PROPERTY_STRUCTURE_ID, ts.getId(),
                                                                       PROPERTY_PLAN_DETAIL_ID, pd.getId()))
                                                .withType(planChannelType)
                                                .build();
                                        channels.add(planChannel);
                                    } else if (pd instanceof PerUnitTariffPlanDetail) {
                                        String channelId = TariffChannelTypeProvider.channelId(TariffChannelTypeProvider.PREFIX_TARIFF_PER_UNIT_RATE, resource, ts, pd);
                                        Map<String, String> propMap = new HashMap<>();
                                        propMap.put(PROPERTY_RESOURCE_ID, resource.getResourceId());
                                        propMap.put(PROPERTY_RESOURCE_NAME, tariffResponse.getName());
                                        propMap.put(PROPERTY_STRUCTURE_ID, ts.getId());
                                        propMap.put(PROPERTY_PLAN_DETAIL_ID, pd.getId());
                                        PerUnitTariffPlanDetail perUnitTariffPlanDetail = (PerUnitTariffPlanDetail) pd;
                                        if (perUnitTariffPlanDetail.getTier() != null) {
                                            propMap.put(PROPERTY_TIER, perUnitTariffPlanDetail.getTier().toString());
                                        }
                                        ChannelTypeUID planChannelType = new ChannelTypeUID(BINDING_ID, channelId);
                                        if (tariffChannelTypeProvider != null) {
                                            tariffChannelTypeProvider.createChannelType(planChannelType, Locale.getDefault(), resource.getName(), perUnitTariffPlanDetail.getTier());
                                        }
                                        Channel planChannel = getCallback().createChannelBuilder(new ChannelUID(getThing().getUID(), channelId),
                                                                                                 planChannelType)
                                                .withProperties(propMap)
                                                .withType(planChannelType)
                                                .build();
                                        channels.add(planChannel);
                                    }
                                });
                            }));
                    }
                }
                if (!channels.isEmpty()) {
                    updateThing(editThing().withChannels(channels).build());
                }
                updateStatus(ThingStatus.ONLINE);
            } catch (AuthenticationFailedException e) {
                String msg = "Unable to authenticate with Glowmarkt API: " + e.getMessage();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
                logger.debug(msg, e);
            } catch (IOException e) {
                String msg = "Unable to fetch resources from Glowmarkt API: " + e.getMessage();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, msg);
                logger.debug(msg, e);
            }
        }).exceptionally(e -> {
            logger.error("Unexpected error initializing " + getThing().getUID(), e);
            return null;
        });
    }

    private static Optional<TariffData> getEffectiveTariff(List<TariffData> resourceTariff, LocalDateTime now) {
        return resourceTariff.stream()
                .sorted(Comparator.comparing(TariffData::getFrom).reversed())
                .filter(td -> td.getFrom().isBefore(now))
                .findFirst();
    }

    private String channelType(Resource resource) {
        return resource.getClassifier().replaceAll("[^\\w-]", "_");
    }

    private String channelId(Resource resource) {
        return resource.getClassifier().replaceAll("[^\\w-]", "_");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (RefreshType.REFRESH.equals(command)) {
            Channel channel = getThing().getChannel(channelUID);
            String resourceId = channel.getProperties().get(PROPERTY_RESOURCE_ID);
            Set<Item> linkedItems = itemChannelLinkRegistry.getLinkedItems(channelUID);
            try {
                if (TariffChannelTypeProvider.isManagedChannelType(channel.getChannelTypeUID())) {
                    TariffResponse resourceTariff = glowmarktService.getResourceTariff(
                            getBridgeHandler().getGlowmarktSession(),
                            getBridgeHandler().getGlowmarktSettings(),
                            resourceId);
                    String structureId = channel.getProperties().get(PROPERTY_STRUCTURE_ID);
                    String planDetailId = channel.getProperties().get(PROPERTY_PLAN_DETAIL_ID);
                    LocalDateTime now = LocalDateTime.now();
                    Optional<TariffData> effectiveTariff = getEffectiveTariff(resourceTariff.getData(), now);
                    effectiveTariff.get().getStructure().stream()
                            .filter(ts -> ts.getId().equals(structureId))
                            .flatMap(ts -> ts.getPlanDetails().stream())
                            .filter(pd -> pd.getId().equals(planDetailId))
                            .findFirst()
                            .ifPresent(tpd -> {
                                updateState(channelUID, new DecimalType((Number) tpd.getAmount()));
                            });
                } else {
                    for (var item : linkedItems) {
                        fetchHistoricData(resourceId, item);
                    }
                }
                updateStatus(ThingStatus.ONLINE);
            } catch (AuthenticationFailedException e) {
                String msg = "Authentication problem fetching resource data: " + e.getMessage();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
                logger.debug(msg, e);
            } catch (IOException e) {
                String msg = "Problem fetching resource data: " + e.getMessage();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, msg);
                logger.debug(msg, e);
            }
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(TariffChannelTypeProvider.class);
    }

    GlowmarktServiceProvider getServiceProvider() {
        return serviceProvider;
    }

    void setTariffChannelTypeProvider(TariffChannelTypeProvider provider) {
        this.tariffChannelTypeProvider = provider;
    }

    private void fetchHistoricData(String resourceId, Item item) throws AuthenticationFailedException, IOException {
        FilterCriteria filterCriteria = new FilterCriteria();
        ZonedDateTime persistenceQueryStartDate = ZonedDateTime.now().minusYears(1);
        filterCriteria.setBeginDate(persistenceQueryStartDate);
        ZonedDateTime persistenceQueryEndDate = ZonedDateTime.now();
        filterCriteria.setEndDate(persistenceQueryEndDate);
        filterCriteria.setItemName(item.getName());
        ModifiablePersistenceService persistenceService = getBridgeHandler().getPersistenceService();
        Iterable<HistoricItem> dataSeries = persistenceService.query(filterCriteria);
        var i = dataSeries.iterator();

        Optional<ZonedDateTime> earliestPersistedTimestamp = stream(dataSeries.spliterator(), true)
                .map(HistoricItem::getTimestamp)
                .reduce((t1, t2) -> t1.isBefore(t2) ? t1 : t2);
        Optional<ZonedDateTime> latestPersistedTimestamp = stream(dataSeries.spliterator(), true)
                .map(HistoricItem::getTimestamp)
                .reduce((t1, t2) -> t1.isAfter(t2) ? t1 : t2);

        if (earliestPersistedTimestamp.map(persistenceQueryStartDate::isBefore).orElse(true)) {
            fetchHistoricDataForMissingPeriod(resourceId, item, persistenceQueryStartDate, earliestPersistedTimestamp);
        }
        if (latestPersistedTimestamp.map(persistenceQueryEndDate::isAfter).orElse(false)) {
            fetchHistoricDataForMissingPeriod(resourceId, item, latestPersistedTimestamp.get(), of(persistenceQueryEndDate));
        }
    }

    private GlowmarktBridgeHandler getBridgeHandler() {
        return (GlowmarktBridgeHandler) getBridge().getHandler();
    }

    private void fetchHistoricDataForMissingPeriod(String resourceId, Item item, ZonedDateTime startDate, Optional<ZonedDateTime> endDate) throws AuthenticationFailedException, IOException {
        Instant firstTime = glowmarktService.getFirstTime(getBridgeHandler().getGlowmarktSession(),
                getBridgeHandler().getGlowmarktSettings(),
                resourceId);
        Instant lastTime = glowmarktService.getLastTime(getBridgeHandler().getGlowmarktSession(),
                getBridgeHandler().getGlowmarktSettings(),
                resourceId);
        Instant fetchStart = !firstTime.isBefore(startDate.toInstant()) ? firstTime : startDate.toInstant();
        Instant fetchEnd;
        if (endDate.isPresent()) {
            fetchEnd = !lastTime.isAfter(endDate.get().toInstant()) ? lastTime : endDate.get().toInstant();
        } else {
            fetchEnd = lastTime;
        }

        if (fetchStart.isBefore(fetchEnd)) {
            batchFetchHistoricData(resourceId, item, fetchStart, fetchEnd);
        }
    }

    private void batchFetchHistoricData(String resourceId, Item item, Instant fetchStart, Instant fetchEnd) throws AuthenticationFailedException, IOException {
        AggregationPeriod aggregationPeriod = PT30M;
        TemporalAmount timeStep = getMaxDuration(aggregationPeriod);
        for (Instant t = fetchStart; t.isBefore(fetchEnd); t = t.plus(timeStep)) {
            Instant t2 = t.plus(timeStep);
            if (t2.isAfter(fetchEnd)) {
                t2 = fetchEnd;
            }
            List<ResourceData> resourceReadings = glowmarktService.getResourceReadings(getBridgeHandler().getGlowmarktSession(),
                    getBridgeHandler().getGlowmarktSettings(),
                    resourceId,
                    t,
                    t2,
                    aggregationPeriod,
                    AggregationFunction.SUM);
            resourceReadings.forEach(r -> {
                        getBridgeHandler().getPersistenceService().store(item, ZonedDateTime.ofInstant(r.getTimestamp(), ZoneId.systemDefault()), new DecimalType(r.getReading()));
                    });
        }
    }

    private TemporalAmount getMaxDuration(AggregationPeriod period) {
        return Map.of(PT30M, ofDays(10),
                PT1H, ofDays(31),
                P1D, ofDays(31),
                P1W, ofDays(6 * 7),
                P1M, ofDays(366),
                P1Y, ofDays(366)).get(period);
    }
}
