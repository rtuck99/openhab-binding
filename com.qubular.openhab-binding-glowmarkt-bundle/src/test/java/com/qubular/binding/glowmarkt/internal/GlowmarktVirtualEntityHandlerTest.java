package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.persistence.ModifiablePersistenceService;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.RefreshType;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.*;
import static com.qubular.glowmarkt.AggregationFunction.SUM;
import static com.qubular.glowmarkt.AggregationPeriod.PT30M;
import static java.time.Instant.parse;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GlowmarktVirtualEntityHandlerTest {
    public static final String VIRTUAL_ENTITY_ID = UUID.randomUUID().toString();
    public static final String BRIDGE_ID = UUID.randomUUID().toString();
    public static final String GAS_CONSUMPTION_RESOURCE_ID = UUID.randomUUID().toString();
    public static final String GAS_COST_RESOURCE_ID = UUID.randomUUID().toString();
    public static final String ELECTRICITY_COST_RESOURCE_ID = UUID.randomUUID().toString();
    public static final String ELECTRICITY_CONSUMPTION_RESOURCE_ID = UUID.randomUUID().toString();
    @Mock
    private Thing virtualEntity;
    @Mock
    private GlowmarktService glowmarktService;
    @Mock
    private HttpClientFactory httpClientFactory;
    @Mock
    private Bridge bridge;
    @Mock
    private ThingHandlerCallback thingHandlerCallback;
    @Mock
    private ModifiablePersistenceService persistenceService;
    @Mock
    private PersistenceServiceRegistry persistenceServiceRegistry;

    @Mock
    private ItemChannelLinkRegistry itemChannelLinkRegistry;
    @Mock
    private CronScheduler cronScheduler;
    @Mock
    private ConfigurationAdmin configurationAdmin;
    @Mock
    private GlowmarktServiceProvider serviceProvider;

    private AutoCloseable mockHandle;
    private GlowmarktBridgeHandler bridgeHandler;
    private MockChannelTypeRegistry channelTypeRegistry;
    private TariffChannelTypeProvider tariffChannelTypeProvider;

    @BeforeEach
    public void setUp() throws IOException {
        mockHandle = MockitoAnnotations.openMocks(this);
        when(bridge.getThingTypeUID()).thenReturn(THING_TYPE_BRIDGE);
        ThingUID bridgeUid = new ThingUID(THING_TYPE_BRIDGE, BRIDGE_ID);
        when(bridge.getUID()).thenReturn(bridgeUid);
        when(virtualEntity.getBridgeUID()).thenReturn(bridgeUid);
        when(virtualEntity.getThingTypeUID()).thenReturn(THING_TYPE_VIRTUAL_ENTITY);
        when(virtualEntity.getUID()).thenReturn(new ThingUID(THING_TYPE_VIRTUAL_ENTITY, VIRTUAL_ENTITY_ID));
        when(thingHandlerCallback.getBridge(bridgeUid)).thenReturn(bridge);
        when(virtualEntity.getProperties()).thenReturn(Map.of(PROPERTY_VIRTUAL_ENTITY_ID, VIRTUAL_ENTITY_ID));
        AtomicReference<Configuration> configuration = new AtomicReference<>(new Configuration(Map.of("username", "testuser",
                                                                               "password", "testpassword",
                                                                               "persistenceService", "mysql",
                                                                                    "maxPastYearsToFetch", 0)));
        doAnswer(invocation -> configuration.get()).when(bridge).getConfiguration();
        doAnswer(invocation -> ChannelBuilder.create((ChannelUID)invocation.getArgument(0))).when(thingHandlerCallback).createChannelBuilder(any(ChannelUID.class), any(ChannelTypeUID.class));
        when(persistenceServiceRegistry.get("mysql")).thenReturn(persistenceService);
        org.osgi.service.cm.Configuration osgiConfig = mock(org.osgi.service.cm.Configuration.class);
        doReturn(osgiConfig).when(configurationAdmin).getConfiguration(anyString());
        Hashtable<Object, Object> osgiConfigProps = new Hashtable<>();
        doReturn(osgiConfigProps).when(osgiConfig).getProperties();
        when(serviceProvider.getItemChannelLinkRegistry()).thenReturn(itemChannelLinkRegistry);
        channelTypeRegistry = new MockChannelTypeRegistry();
        when(serviceProvider.getChannelTypeRegistry()).thenReturn(channelTypeRegistry);
        tariffChannelTypeProvider = new TariffChannelTypeProvider(channelTypeRegistry);
        channelTypeRegistry.addChannelTypeProvider(tariffChannelTypeProvider);
        when(serviceProvider.getTariffChannelTypeProvider()).thenReturn(tariffChannelTypeProvider);
        channelTypeRegistry.addChannelTypeProvider(new XmlChannelTypeProvider());
    }

    private static class MockChannelTypeRegistry extends ChannelTypeRegistry {
        @Override
        public void addChannelTypeProvider(ChannelTypeProvider channelTypeProviders) {
            super.addChannelTypeProvider(channelTypeProviders);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockHandle.close();
        if (bridgeHandler != null) {
            bridgeHandler.dispose();
            bridgeHandler = null;
        }
    }

    private void successfullyAuthenticate() throws AuthenticationFailedException, IOException {
        when(glowmarktService.authenticate(any(GlowmarktSettings.class), anyString(), anyString()))
                .thenReturn(new GlowmarktSession(Instant.now().plus(1, ChronoUnit.DAYS), "mytoken"));
    }

    private void gasAndElectricityDccMeter() throws AuthenticationFailedException, IOException {
        when(glowmarktService.getVirtualEntity(any(GlowmarktSession.class),
                any(GlowmarktSettings.class),
                eq(VIRTUAL_ENTITY_ID)))
                .thenReturn(new VirtualEntity.Builder()
                        .withName("DCC Sourced")
                        .withVeId(VIRTUAL_ENTITY_ID)
                        .withVeTypeId(UUID.randomUUID().toString())
                        .withResources(List.of(
                                new Resource.Builder()
                                        .withName("gas consumption")
                                        .withActive(true)
                                        .withClassifier("gas.consumption")
                                        .withDescription("gas consumption DCC SM profile reads")
                                        .withBaseUnit("kWh")
                                        .withResourceId(GAS_CONSUMPTION_RESOURCE_ID)
                                .build(),
                                new Resource.Builder()
                                        .withName("gas cost")
                                        .withActive(true)
                                        .withClassifier("gas.consumption.cost")
                                        .withDescription("gas cost DCC SM profile reads")
                                        .withBaseUnit("pence")
                                        .withResourceId(GAS_COST_RESOURCE_ID)
                                .build(),
                                new Resource.Builder()
                                        .withName("electricity cost")
                                        .withActive(true)
                                        .withClassifier("electricity.consumption.cost")
                                        .withDescription("electricity cost DCC SM profile reads")
                                        .withBaseUnit("pence")
                                        .withResourceId(ELECTRICITY_COST_RESOURCE_ID)
                                .build(),
                                new Resource.Builder()
                                        .withName("electricity consumption")
                                        .withActive(true)
                                        .withClassifier("electricity.consumption")
                                        .withDescription("electricity consumption DCC SM profile reads")
                                        .withBaseUnit("kWh")
                                        .withResourceId(ELECTRICITY_CONSUMPTION_RESOURCE_ID)
                                .build()
                                ))
                        .build());
        when(glowmarktService.getResourceTariff(any(GlowmarktSession.class),
                                                any(GlowmarktSettings.class),
                                                eq(GAS_CONSUMPTION_RESOURCE_ID)))
                .thenReturn(new TariffResponse.Builder().withData(emptyList()).build());
        when(glowmarktService.getResourceTariff(any(GlowmarktSession.class),
                                                any(GlowmarktSettings.class),
                                                eq(ELECTRICITY_CONSUMPTION_RESOURCE_ID)))
                .thenReturn(new TariffResponse.Builder().withData(List.of(new TariffData.Builder()
                                            .withFrom(LocalDateTime.parse("2022-10-18T22:19:00"))
                                            .withStructure(List.of(new TariffStructure.Builder()
                                                                           .withSource("DCC")
                                                                           .withWeekName("1")
                                                                           .withPlanDetail(List.of(
                                                                                   new StandingChargeTariffPlanDetail(new BigDecimal("44.4")),
                                                                                   new PerUnitTariffPlanDetail(1, new BigDecimal("34.22"))
                                                                           ))
                                                                           .build()))
                                            .build()))
                                    .withName("electricity consumption")
                                    .build());
    }

    private void bridgeAndVirtualEntityWithChannels() {
        ChannelUID channelUID = new ChannelUID(virtualEntity.getUID(), String.format(
                "tariff_standing_charge_%s_default_structure_standing_charge", ELECTRICITY_COST_RESOURCE_ID));
        when(virtualEntity.getChannels()).thenReturn(List.of(
                ChannelBuilder.create(channelUID, "Number:Energy")
                        .withType(new ChannelTypeUID(BINDING_ID, channelUID.getId()))
                        .withProperties(Map.of(PROPERTY_RESOURCE_ID, ELECTRICITY_COST_RESOURCE_ID,
                                               PROPERTY_RESOURCE_NAME, "electricity cost",
                                               PROPERTY_STRUCTURE_ID, "default_structure",
                                               PROPERTY_PLAN_DETAIL_ID, "standing_charge"))
                        .build()
        ));
        ThingRegistry thingRegistry = mock(ThingRegistry.class);
        when(thingRegistry.get(virtualEntity.getUID())).thenReturn(virtualEntity);
        when(thingRegistry.get(bridge.getUID())).thenReturn(bridge);
        when(thingRegistry.getAll()).thenReturn(List.of(virtualEntity, bridge));

        when(serviceProvider.getThingRegistry()).thenReturn(thingRegistry);
    }

    @Test
    public void initialisationCreatesChannels() throws AuthenticationFailedException, IOException {
        successfullyAuthenticate();
        gasAndElectricityDccMeter();

        ThingHandler handler = createThingHandler();

        handler.initialize();

        ArgumentCaptor<ThingStatusInfo> statusCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        verify(thingHandlerCallback, timeout(3000)).statusUpdated(any(Thing.class), statusCaptor.capture());
        assertEquals(ThingStatus.ONLINE, statusCaptor.getValue().getStatus());
        ArgumentCaptor<Thing> thingArgumentCaptor = ArgumentCaptor.forClass(Thing.class);
        verify(thingHandlerCallback).thingUpdated(thingArgumentCaptor.capture());
        List<Channel> channels = thingArgumentCaptor.getValue().getChannels();
        assertEquals(6, channels.size());
        assertEquals("gas_consumption", channels.get(0).getChannelTypeUID().getId());
        assertEquals("gas.consumption", channels.get(0).getProperties().get("classifier"));
        assertEquals(GAS_CONSUMPTION_RESOURCE_ID, channels.get(0).getProperties().get("resourceId"));
        assertEquals("gas_consumption_cost", channels.get(1).getChannelTypeUID().getId());
        assertEquals("gas.consumption.cost", channels.get(1).getProperties().get("classifier"));
        assertEquals(GAS_COST_RESOURCE_ID, channels.get(1).getProperties().get("resourceId"));
        assertEquals("electricity_consumption_cost", channels.get(2).getChannelTypeUID().getId());
        assertEquals("electricity.consumption.cost", channels.get(2).getProperties().get("classifier"));
        assertEquals(ELECTRICITY_COST_RESOURCE_ID, channels.get(2).getProperties().get("resourceId"));
        assertEquals("electricity_consumption", channels.get(3).getChannelTypeUID().getId());
        assertEquals("electricity.consumption", channels.get(3).getProperties().get("classifier"));
        assertEquals(ELECTRICITY_CONSUMPTION_RESOURCE_ID, channels.get(3).getProperties().get("resourceId"));
        assertEquals("tariff_standing_charge_" + ELECTRICITY_CONSUMPTION_RESOURCE_ID + "_week_1_standing_charge", channels.get(4).getChannelTypeUID().getId());
        assertEquals("electricity consumption", channels.get(4).getProperties().get(PROPERTY_RESOURCE_NAME));
        assertEquals(ELECTRICITY_CONSUMPTION_RESOURCE_ID, channels.get(4).getProperties().get("resourceId"));
        assertEquals("tariff_per_unit_rate_" + ELECTRICITY_CONSUMPTION_RESOURCE_ID + "_week_1_rate_1", channels.get(5).getChannelTypeUID().getId());
        assertEquals("electricity consumption", channels.get(5).getProperties().get(PROPERTY_RESOURCE_NAME));
        assertEquals("1", channels.get(5).getProperties().get(PROPERTY_TIER));
        assertEquals(ELECTRICITY_CONSUMPTION_RESOURCE_ID, channels.get(5).getProperties().get("resourceId"));
    }

    private ThingHandler createThingHandler() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, serviceProvider, httpClientFactory,
                                                                      persistenceServiceRegistry,
                                                                      cronScheduler,
                                                                      configurationAdmin);
        bridgeHandler = (GlowmarktBridgeHandler) factory.createHandler(bridge);
        when(bridge.getHandler()).thenReturn(bridgeHandler);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        bridgeHandler.initialize();
        ThingHandler handler = factory.createHandler(virtualEntity);
        handler.setCallback(thingHandlerCallback);
        return handler;
    }

    @Test
    public void authenticationFailureExceptionUpdatesStatus() throws AuthenticationFailedException, IOException {
        when(glowmarktService.authenticate(any(GlowmarktSettings.class), anyString(), anyString()))
                .thenThrow(new AuthenticationFailedException("test exception"));
        gasAndElectricityDccMeter();

        ThingHandler thingHandler = createThingHandler();
        thingHandler.initialize();

        ArgumentCaptor<ThingStatusInfo> statusCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        verify(thingHandlerCallback, timeout(3000)).statusUpdated(any(Thing.class), statusCaptor.capture());
        assertEquals(ThingStatus.OFFLINE, statusCaptor.getValue().getStatus());
    }

    @Test
    public void ioExceptionUpdatesStatus() throws AuthenticationFailedException, IOException {
        when(glowmarktService.authenticate(any(GlowmarktSettings.class), anyString(), anyString()))
                .thenThrow(new IOException("test exception"));
        gasAndElectricityDccMeter();

        ThingHandler thingHandler = createThingHandler();
        thingHandler.initialize();

        ArgumentCaptor<ThingStatusInfo> statusCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        verify(thingHandlerCallback, timeout(3000)).statusUpdated(any(Thing.class), statusCaptor.capture());
        assertEquals(ThingStatus.OFFLINE, statusCaptor.getValue().getStatus());
    }

    @Test
    public void refreshCommandUpdatesChannel() throws AuthenticationFailedException, IOException {
        successfullyAuthenticate();
        gasAndElectricityDccMeter();

        ThingHandler thingHandler = createThingHandler();
        thingHandler.initialize();

        verify(thingHandlerCallback, timeout(3000)).statusUpdated(any(Thing.class), any(ThingStatusInfo.class));

        Item item = mock(Item.class);
        when(itemChannelLinkRegistry.getLinkedItems(
                new ChannelUID(new ThingUID(THING_TYPE_VIRTUAL_ENTITY, VIRTUAL_ENTITY_ID), "gas_consumption")))
                .thenReturn(Set.of(item));
        when(glowmarktService.getFirstTime(any(GlowmarktSession.class), any(GlowmarktSettings.class),
                                           eq(GAS_CONSUMPTION_RESOURCE_ID)))
                .thenReturn(parse("2022-01-01T00:00:00Z"));
        when(glowmarktService.getLastTime(any(GlowmarktSession.class), any(GlowmarktSettings.class),
                                          eq(GAS_CONSUMPTION_RESOURCE_ID)))
                .thenReturn(parse("2022-02-01T00:00:00Z"));
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class),
                                                  eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-01T00:00:00Z")),
                                                  eq(parse("2022-01-11T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(List.of(new ResourceData(1.0, parse("2022-01-01T00:00:00Z")),
                                    new ResourceData(1.1, parse("2022-01-01T00:30:00Z"))));
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class),
                                                  eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-11T00:00:00Z")),
                                                  eq(parse("2022-01-21T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(List.of(new ResourceData(0.9, parse("2022-01-11T00:00:00Z")),
                                    new ResourceData(1.12, parse("2022-01-11T00:30:00Z"))));
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class),
                                                  eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-21T00:00:00Z")),
                                                  eq(parse("2022-01-31T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(emptyList());
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class),
                                                  eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-31T00:00:00Z")),
                                                  eq(parse("2022-02-01T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(List.of(new ResourceData(1.4, parse("2022-01-31T00:00:00Z")),
                                    new ResourceData(20.3, parse("2022-01-31T00:30:00Z"))));

        ThingUID virtualEntityUID = virtualEntity.getUID();
        thingHandler.handleCommand(new ChannelUID(virtualEntityUID, "gas_consumption"), RefreshType.REFRESH);

        verify(glowmarktService).authenticate(any(GlowmarktSettings.class), eq("testuser"), eq("testpassword"));
        verifyReadingForTimes("2022-01-01T00:00:00Z", "2022-01-11T00:00:00Z");
        verifyReadingForTimes("2022-01-11T00:00:00Z", "2022-01-21T00:00:00Z");
        verifyReadingForTimes("2022-01-21T00:00:00Z", "2022-01-31T00:00:00Z");
        verifyReadingForTimes("2022-01-31T00:00:00Z", "2022-02-01T00:00:00Z");

        verify(persistenceService).store(same(item), eq(ZonedDateTime.ofInstant(parse("2022-01-01T00:00:00Z"),
                                                                                ZoneId.systemDefault())),
                                         eq(new DecimalType("1.0")));
        verify(persistenceService).store(same(item), eq(ZonedDateTime.ofInstant(parse("2022-01-01T00:30:00Z"),
                                                                                ZoneId.systemDefault())),
                                         eq(new DecimalType("1.1")));
        verify(persistenceService).store(same(item), eq(ZonedDateTime.ofInstant(parse("2022-01-11T00:00:00Z"),
                                                                                ZoneId.systemDefault())),
                                         eq(new DecimalType("0.9")));
        verify(persistenceService).store(same(item), eq(ZonedDateTime.ofInstant(parse("2022-01-11T00:30:00Z"),
                                                                                ZoneId.systemDefault())),
                                         eq(new DecimalType("1.12")));
        verify(persistenceService).store(same(item), eq(ZonedDateTime.ofInstant(parse("2022-01-31T00:00:00Z"),
                                                                                ZoneId.systemDefault())),
                                         eq(new DecimalType("1.4")));
        verify(persistenceService).store(same(item), eq(ZonedDateTime.ofInstant(parse("2022-01-31T00:30:00Z"),
                                                                                ZoneId.systemDefault())),
                                         eq(new DecimalType("20.3")));
    }

    @Test
    public void refreshCommandFetchesRates() throws AuthenticationFailedException, IOException {
        successfullyAuthenticate();
        gasAndElectricityDccMeter();

        ThingHandler thingHandler = createThingHandler();
        thingHandler.initialize();
        verify(thingHandlerCallback, timeout(3000)).statusUpdated(any(Thing.class), any(ThingStatusInfo.class));

        ThingUID virtualEntityUID = virtualEntity.getUID();
        ChannelUID standingChargeChannelId = new ChannelUID(virtualEntityUID, "tariff_standing_charge_" + ELECTRICITY_CONSUMPTION_RESOURCE_ID + "_week_1_standing_charge");
        thingHandler.handleCommand(standingChargeChannelId, RefreshType.REFRESH);

        verify(thingHandlerCallback, timeout(1000)).stateUpdated(standingChargeChannelId, DecimalType.valueOf("44.4"));


        ChannelUID perUnitRateChannelId = new ChannelUID(virtualEntityUID, "tariff_per_unit_rate_" + ELECTRICITY_CONSUMPTION_RESOURCE_ID + "_week_1_rate_1");
        thingHandler.handleCommand(perUnitRateChannelId, RefreshType.REFRESH);
        verify(thingHandlerCallback, timeout(1000)).stateUpdated(perUnitRateChannelId, DecimalType.valueOf("34.22"));
    }

    @Test
    public void channelsPreloadedAtStartup() throws AuthenticationFailedException, IOException {
        successfullyAuthenticate();
        gasAndElectricityDccMeter();
        bridgeAndVirtualEntityWithChannels();

        new ChannelTypeManager(serviceProvider);
        Collection<ChannelType> channelTypes = tariffChannelTypeProvider.getChannelTypes(Locale.getDefault());
        assertEquals(1, channelTypes.size());

        ChannelType channelType = channelTypes.iterator().next();
        assertEquals("electricity cost Tariff Standing Charge", channelType.getLabel());
        assertEquals("The standing charge", channelType.getDescription());
        assertEquals(true, channelType.getState().isReadOnly());
        assertEquals(String.format("tariff_standing_charge_%s_default_structure_standing_charge",ELECTRICITY_COST_RESOURCE_ID), channelType.getUID().getId());
    }

    private void verifyReadingForTimes(String from, String to) throws IOException, AuthenticationFailedException {
        verify(glowmarktService).getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse(from)), eq(parse(to)), eq(PT30M), eq(AggregationFunction.SUM));
    }

}