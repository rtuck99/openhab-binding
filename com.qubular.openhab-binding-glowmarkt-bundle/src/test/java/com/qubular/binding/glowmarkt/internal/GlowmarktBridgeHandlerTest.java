package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.discovery.DiscoveryListener;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.scheduler.SchedulerRunnable;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.types.RefreshType;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GlowmarktBridgeHandlerTest {

    public static final String METER_1_ID = UUID.randomUUID().toString();
    public static final String METER_2_ID = UUID.randomUUID().toString();
    public static final String BRIDGE_UID = UUID.randomUUID().toString();
    public static final ChannelUID CHILD_CHANNEL_UID = new ChannelUID(new ThingUID(THING_TYPE_VIRTUAL_ENTITY, "1"), "1");
    @Mock
    private Bridge bridge;

    @Mock
    private GlowmarktService glowmarktService;
    @Mock
    private HttpClientFactory httpClientFactory;
    @Mock
    private ItemChannelLinkRegistry itemChannelLinkRegistry;
    @Mock
    private PersistenceServiceRegistry persistenceServiceRegistry;
    @Mock
    private CronScheduler cronScheduler;
    @Mock
    private ConfigurationAdmin configurationAdmin;

    private AutoCloseable mockHandle;
    private ThingHandler bridgeHandler;

    @BeforeEach
    public void setUp() throws IOException {
        mockHandle = MockitoAnnotations.openMocks(this);
        when(bridge.getThingTypeUID()).thenReturn(THING_TYPE_BRIDGE);
        when(bridge.getUID()).thenReturn(new ThingUID(THING_TYPE_BRIDGE, BRIDGE_UID));
        Configuration configuration = new Configuration(Map.of("username", "testuser",
                "password", "testpassword",
                "cronSchedule", "30 4 * * *"));
        when(bridge.getConfiguration()).thenReturn(configuration);
        org.osgi.service.cm.Configuration osgiConfig = mock(org.osgi.service.cm.Configuration.class);
        doReturn(osgiConfig).when(configurationAdmin).getConfiguration(anyString());
        doReturn(new Hashtable<>()).when(osgiConfig).getProperties();
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

    private void gasAndElectricityMeter() throws AuthenticationFailedException, IOException {
        when(glowmarktService.getVirtualEntities(any(GlowmarktSession.class), any(GlowmarktSettings.class)))
                .thenReturn(List.of(
                        new VirtualEntity.Builder().withVeId(METER_1_ID).build(),
                        new VirtualEntity.Builder().withVeId(METER_2_ID).build()
                ));
    }

    @Test
    public void hasDiscoveryService() {
        ThingHandler handler = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry, cronScheduler, configurationAdmin).createHandler(bridge);
        assertTrue(handler.getServices().stream().anyMatch(DiscoveryService.class::isAssignableFrom));
    }

    @Test
    public void discoveryServiceDiscoversVirtualEntity() throws AuthenticationFailedException, IOException {
        successfullyAuthenticate();
        gasAndElectricityMeter();

        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        bridgeHandler = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry, cronScheduler, configurationAdmin).createHandler(bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        bridgeHandler.initialize();
        GlowmarktDiscoveryService discoveryService = new GlowmarktDiscoveryService();
        discoveryService.addDiscoveryListener(discoveryListener);
        discoveryService.setThingHandler(bridgeHandler);

        ArgumentCaptor<DiscoveryResult> resultCaptor = ArgumentCaptor.forClass(DiscoveryResult.class);
        verify(glowmarktService, timeout(3000)).authenticate(any(GlowmarktSettings.class), eq("testuser"), eq("testpassword"));
        verify(discoveryListener, timeout(3000).times(2)).thingDiscovered(same(discoveryService), resultCaptor.capture());

        assertEquals(METER_1_ID, resultCaptor.getAllValues().get(0).getProperties().get(PROPERTY_VIRTUAL_ENTITY_ID));
        assertEquals(bridge.getUID(), resultCaptor.getAllValues().get(0).getBridgeUID());
        assertEquals(PROPERTY_VIRTUAL_ENTITY_ID, resultCaptor.getAllValues().get(0).getRepresentationProperty());
        assertEquals(new ThingUID(THING_TYPE_VIRTUAL_ENTITY, METER_1_ID, BRIDGE_UID), resultCaptor.getAllValues().get(0).getThingUID());

        assertEquals(METER_2_ID, resultCaptor.getAllValues().get(1).getProperties().get(PROPERTY_VIRTUAL_ENTITY_ID));
        assertEquals(bridge.getUID(), resultCaptor.getAllValues().get(1).getBridgeUID());
        assertEquals(PROPERTY_VIRTUAL_ENTITY_ID, resultCaptor.getAllValues().get(1).getRepresentationProperty());
        assertEquals(new ThingUID(THING_TYPE_VIRTUAL_ENTITY, METER_2_ID, BRIDGE_UID), resultCaptor.getAllValues().get(1).getThingUID());
    }

    @Test
    public void discoveryServiceUpdatesStatusOnIOException() throws AuthenticationFailedException, IOException {
        when(glowmarktService.authenticate(any(GlowmarktSettings.class), anyString(), anyString()))
                .thenThrow(new IOException("test exception"));

        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        bridgeHandler = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry, cronScheduler, configurationAdmin).createHandler(bridge);
        ThingHandlerCallback thingHandlerCallback = mock(ThingHandlerCallback.class);
        bridgeHandler.setCallback(thingHandlerCallback);
        bridgeHandler.initialize();
        ArgumentCaptor<ThingStatusInfo> statusCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        InOrder inOrder = inOrder(thingHandlerCallback);
        inOrder.verify(thingHandlerCallback, timeout(3000)).statusUpdated(same(bridge), statusCaptor.capture());
        assertEquals(ThingStatus.UNKNOWN, statusCaptor.getValue().getStatus());

        GlowmarktDiscoveryService discoveryService = new GlowmarktDiscoveryService();
        discoveryService.addDiscoveryListener(discoveryListener);
        discoveryService.setThingHandler(bridgeHandler);

        inOrder.verify(thingHandlerCallback, timeout(3000)).statusUpdated(same(bridge), statusCaptor.capture());
        assertEquals(ThingStatus.OFFLINE, statusCaptor.getValue().getStatus());
        assertEquals(ThingStatusDetail.COMMUNICATION_ERROR, statusCaptor.getValue().getStatusDetail());
    }

    @Test
    public void discoveryServiceUpdatesStatusOnAuthenticationException() throws AuthenticationFailedException, IOException {
        when(glowmarktService.authenticate(any(GlowmarktSettings.class), anyString(), anyString()))
                .thenThrow(new AuthenticationFailedException("test exception"));

        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        bridgeHandler = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry, cronScheduler, configurationAdmin).createHandler(bridge);
        ThingHandlerCallback thingHandlerCallback = mock(ThingHandlerCallback.class);
        InOrder inOrder = inOrder(thingHandlerCallback);
        bridgeHandler.setCallback(thingHandlerCallback);
        bridgeHandler.initialize();
        ArgumentCaptor<ThingStatusInfo> statusCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        inOrder.verify(thingHandlerCallback, timeout(3000)).statusUpdated(same(bridge), statusCaptor.capture());

        GlowmarktDiscoveryService discoveryService = new GlowmarktDiscoveryService();
        discoveryService.addDiscoveryListener(discoveryListener);
        discoveryService.setThingHandler(bridgeHandler);

        inOrder.verify(thingHandlerCallback, timeout(3000)).statusUpdated(same(bridge), statusCaptor.capture());
        assertEquals(ThingStatus.UNINITIALIZED, statusCaptor.getValue().getStatus());
        assertEquals(ThingStatusDetail.CONFIGURATION_ERROR, statusCaptor.getValue().getStatusDetail());
    }

    @Test
    public void initialiseUpdatesStatus() {
        ThingHandler handler = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry, cronScheduler, configurationAdmin).createHandler(bridge);
        ThingHandlerCallback thingHandlerCallback = mock(ThingHandlerCallback.class);
        handler.setCallback(thingHandlerCallback);
        handler.initialize();

        ArgumentCaptor<ThingStatusInfo> statusCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        verify(thingHandlerCallback).statusUpdated(same(bridge), statusCaptor.capture());
        assertEquals(ThingStatus.UNKNOWN, statusCaptor.getValue().getStatus());
    }

    @Test
    public void initialiseSchedulesUpdate() throws AuthenticationFailedException, IOException {
        successfullyAuthenticate();
        gasAndElectricityMeter();
        ThingHandler childHandler = childVirtualEntityThingHandler();

        ThingHandler handler = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry, cronScheduler, configurationAdmin).createHandler(bridge);
        ThingHandlerCallback thingHandlerCallback = mock(ThingHandlerCallback.class);
        handler.setCallback(thingHandlerCallback);
        handler.initialize();

        verify(childHandler, timeout(30000)).handleCommand(CHILD_CHANNEL_UID, RefreshType.REFRESH);
        verify(cronScheduler).schedule(any(SchedulerRunnable.class), eq("30 4 * * *"));
    }

    private ThingHandler childVirtualEntityThingHandler() {
        Thing childThing = mock(Thing.class);
        ThingHandler childHandler = mock(ThingHandler.class);
        Channel childChannel = ChannelBuilder.create(CHILD_CHANNEL_UID).build();
        when(childThing.getChannels()).thenReturn(List.of(childChannel));
        when(childThing.getHandler()).thenReturn(childHandler);
        when(bridge.getThings()).thenReturn(List.of(childThing));

        return childHandler;
    }
}