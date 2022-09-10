package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.GlowmarktService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.THING_TYPE_BRIDGE;
import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.THING_TYPE_VIRTUAL_ENTITY;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GlowmarktHandlerFactoryTest {
    @Mock
    private Bridge bridge;

    @Mock
    private Thing virtualEntity;

    @Mock
    GlowmarktService glowmarktService;

    @Mock
    HttpClientFactory httpClientFactory;

    @Mock
    private ItemChannelLinkRegistry itemChannelLinkRegistry;

    private AutoCloseable mockHandle;
    @Mock
    private PersistenceServiceRegistry persistenceServiceRegistry;

    @BeforeEach
    public void setUp() {
        mockHandle = MockitoAnnotations.openMocks(this);

        when(bridge.getThingTypeUID()).thenReturn(THING_TYPE_BRIDGE);
        when(virtualEntity.getThingTypeUID()).thenReturn(THING_TYPE_VIRTUAL_ENTITY);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockHandle.close();
    }

    @Test
    public void supportsBridgeType() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry);

        assertTrue(factory.supportsThingType(THING_TYPE_BRIDGE));
    }

    @Test
    public void supportsVirtualEntityType() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry);

        assertTrue(factory.supportsThingType(THING_TYPE_VIRTUAL_ENTITY));
    }

    @Test
    public void createsBridgeHandler() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry);
        ThingHandler handler = factory.createHandler(bridge);
        assertNotNull(handler);
    }

    @Test
    public void createsVirtualEntityHandler() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, httpClientFactory, persistenceServiceRegistry, itemChannelLinkRegistry);
        ThingHandler handler = factory.createHandler(virtualEntity);
        assertNotNull(handler);
    }

}