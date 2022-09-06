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
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.RefreshType;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
    private ItemChannelLinkRegistry itemChannelLinkRegistry;

    private AutoCloseable mockHandle;

    @BeforeEach
    public void setUp() {
        mockHandle = MockitoAnnotations.openMocks(this);
        when(bridge.getThingTypeUID()).thenReturn(THING_TYPE_BRIDGE);
        ThingUID bridgeUid = new ThingUID(THING_TYPE_BRIDGE, BRIDGE_ID);
        when(bridge.getUID()).thenReturn(bridgeUid);
        when(virtualEntity.getBridgeUID()).thenReturn(bridgeUid);
        when(virtualEntity.getThingTypeUID()).thenReturn(THING_TYPE_VIRTUAL_ENTITY);
        when(virtualEntity.getUID()).thenReturn(new ThingUID(THING_TYPE_VIRTUAL_ENTITY, VIRTUAL_ENTITY_ID));
        when(thingHandlerCallback.getBridge(bridgeUid)).thenReturn(bridge);
        when(virtualEntity.getProperties()).thenReturn(Map.of(PROPERTY_VIRTUAL_ENTITY_ID, VIRTUAL_ENTITY_ID));
        Configuration configuration = mock(Configuration.class);
        Map<String, Object> config = Map.of("username", "testuser",
                "password", "testpassword");
        when(configuration.getProperties()).thenReturn(config);
        doAnswer(invocation -> config.get(invocation.getArgument(0))).when(configuration).get(anyString());
        when(bridge.getConfiguration()).thenReturn(configuration);
        doAnswer(invocation -> ChannelBuilder.create((ChannelUID)invocation.getArgument(0))).when(thingHandlerCallback).createChannelBuilder(any(ChannelUID.class), any(ChannelTypeUID.class));
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockHandle.close();
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
        assertEquals(4, channels.size());
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
    }

    private ThingHandler createThingHandler() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceService, itemChannelLinkRegistry);
        GlowmarktBridgeHandler bridgeHandler = (GlowmarktBridgeHandler) factory.createHandler(bridge);
        when(bridge.getHandler()).thenReturn(bridgeHandler);
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
        assertEquals(ThingStatus.UNINITIALIZED, statusCaptor.getValue().getStatus());
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
        assertEquals(ThingStatus.UNINITIALIZED, statusCaptor.getValue().getStatus());
    }

    @Test
    public void refreshCommandUpdatesChannel() throws AuthenticationFailedException, IOException {
        successfullyAuthenticate();
        gasAndElectricityDccMeter();

        ThingHandler thingHandler = createThingHandler();
        thingHandler.initialize();

        verify(thingHandlerCallback, timeout(3000)).statusUpdated(any(Thing.class), any(ThingStatusInfo.class));

        Item item = mock(Item.class);
        when(itemChannelLinkRegistry.getLinkedItems(new ChannelUID(new ThingUID(THING_TYPE_VIRTUAL_ENTITY, VIRTUAL_ENTITY_ID), "gas_consumption")))
                .thenReturn(Set.of(item));
        when(glowmarktService.getFirstTime(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID)))
                .thenReturn(parse("2022-01-01T00:00:00Z"));
        when(glowmarktService.getLastTime(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID)))
                .thenReturn(parse("2022-02-01T00:00:00Z"));
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-01T00:00:00Z")), eq(parse("2022-01-11T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(List.of(new ResourceData(1.0, parse("2022-01-01T00:00:00Z")),
                        new ResourceData(1.1, parse("2022-01-01T00:30:00Z"))));
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-11T00:00:00Z")), eq(parse("2022-01-21T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(List.of(new ResourceData(0.9, parse("2022-01-11T00:00:00Z")),
                        new ResourceData(1.12, parse("2022-01-11T00:30:00Z"))));
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-21T00:00:00Z")), eq(parse("2022-01-31T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(emptyList());
        when(glowmarktService.getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse("2022-01-31T00:00:00Z")), eq(parse("2022-02-01T00:00:00Z")), eq(PT30M), eq(SUM)))
                .thenReturn(List.of(new ResourceData(1.4, parse("2022-01-31T00:00:00Z")),
                        new ResourceData(20.3, parse("2022-01-31T00:30:00Z"))));

        ThingUID virtualEntityUID = virtualEntity.getUID();
        thingHandler.handleCommand(new ChannelUID(virtualEntityUID, "gas_consumption"), RefreshType.REFRESH);

        verify(glowmarktService).authenticate(any(GlowmarktSettings.class), eq("testuser"), eq("testpassword"));
        verifyReadingForTimes("2022-01-01T00:00:00Z", "2022-01-11T00:00:00Z");
        verifyReadingForTimes("2022-01-11T00:00:00Z", "2022-01-21T00:00:00Z");
        verifyReadingForTimes("2022-01-21T00:00:00Z", "2022-01-31T00:00:00Z");
        verifyReadingForTimes("2022-01-31T00:00:00Z", "2022-02-01T00:00:00Z");

        verify(persistenceService).store(same(item), eq(Date.from(parse("2022-01-01T00:00:00Z"))), eq(new DecimalType("1.0")));
        verify(persistenceService).store(same(item), eq(Date.from(parse("2022-01-01T00:30:00Z"))), eq(new DecimalType("1.1")));
        verify(persistenceService).store(same(item), eq(Date.from(parse("2022-01-11T00:00:00Z"))), eq(new DecimalType("0.9")));
        verify(persistenceService).store(same(item), eq(Date.from(parse("2022-01-11T00:30:00Z"))), eq(new DecimalType("1.12")));
        verify(persistenceService).store(same(item), eq(Date.from(parse("2022-01-31T00:00:00Z"))), eq(new DecimalType("1.4")));
        verify(persistenceService).store(same(item), eq(Date.from(parse("2022-01-31T00:30:00Z"))), eq(new DecimalType("20.3")));
    }

    private void verifyReadingForTimes(String from, String to) throws IOException, AuthenticationFailedException {
        verify(glowmarktService).getResourceReadings(any(GlowmarktSession.class), any(GlowmarktSettings.class), eq(GAS_CONSUMPTION_RESOURCE_ID), eq(parse(from)), eq(parse(to)), eq(PT30M), eq(AggregationFunction.SUM));
    }

}