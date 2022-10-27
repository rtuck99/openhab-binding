package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.GlowmarktService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.util.Hashtable;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.THING_TYPE_BRIDGE;
import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.THING_TYPE_VIRTUAL_ENTITY;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
    @Mock
    private ConfigurationAdmin configurationAdmin;

    private AutoCloseable mockHandle;
    @Mock
    private PersistenceServiceRegistry persistenceServiceRegistry;
    @Mock
    private CronScheduler cronScheduler;
    @Mock
    private GlowmarktServiceProvider serviceProvider;

    @BeforeEach
    public void setUp() throws IOException {
        mockHandle = MockitoAnnotations.openMocks(this);

        when(bridge.getThingTypeUID()).thenReturn(THING_TYPE_BRIDGE);
        when(virtualEntity.getThingTypeUID()).thenReturn(THING_TYPE_VIRTUAL_ENTITY);
        Configuration config = mock(Configuration.class);
        doReturn(config).when(configurationAdmin).getConfiguration(anyString());
        doReturn(new Hashtable<>()).when(config).getProperties();
        when(serviceProvider.getChannelTypeRegistry()).thenReturn(new ChannelTypeRegistry());
        when(serviceProvider.getItemChannelLinkRegistry()).thenReturn(itemChannelLinkRegistry);
        doReturn(new org.openhab.core.config.core.Configuration()).when(bridge).getConfiguration();
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockHandle.close();
    }

    @Test
    public void supportsBridgeType() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, serviceProvider, httpClientFactory,
                                                                      persistenceServiceRegistry,
                                                                      cronScheduler,
                                                                      configurationAdmin);

        assertTrue(factory.supportsThingType(THING_TYPE_BRIDGE));
    }

    @Test
    public void supportsVirtualEntityType() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, serviceProvider, httpClientFactory,
                                                                      persistenceServiceRegistry,
                                                                      cronScheduler,
                                                                      configurationAdmin);

        assertTrue(factory.supportsThingType(THING_TYPE_VIRTUAL_ENTITY));
    }

    @Test
    public void createsBridgeHandler() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, serviceProvider, httpClientFactory,
                                                                      persistenceServiceRegistry,
                                                                      cronScheduler,
                                                                      configurationAdmin);
        ThingHandler handler = factory.createHandler(bridge);
        assertNotNull(handler);
    }

    @Test
    public void createsVirtualEntityHandler() {
        GlowmarktHandlerFactory factory = new GlowmarktHandlerFactory(glowmarktService, serviceProvider, httpClientFactory,
                                                                      persistenceServiceRegistry,
                                                                      cronScheduler,
                                                                      configurationAdmin);
        ThingHandler handler = factory.createHandler(virtualEntity);
        assertNotNull(handler);
    }

}