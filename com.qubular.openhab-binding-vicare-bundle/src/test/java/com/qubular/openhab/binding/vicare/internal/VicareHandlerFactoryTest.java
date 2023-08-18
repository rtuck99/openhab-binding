package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.vicare.VicareService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.BINDING_ID;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VicareHandlerFactoryTest {
    public static final ThingTypeUID UNKNOWN_DEVICE_THING_TYPE = new ThingTypeUID(BINDING_ID, "unknown_device_thing");
    @Mock
    private BundleContext bundleContext;
    @Mock
    private VicareServiceProvider vicareServiceProvider;
    @Mock
    private Bundle bundle;
    @Mock
    private VicareService vicareService;
    private AutoCloseable mockHandle;


    @BeforeEach
    public void setUp() {
        mockHandle = MockitoAnnotations.openMocks(this);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(vicareServiceProvider.getVicareService()).thenReturn(vicareService);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockHandle.close();
    }

    @Test
    public void createsHandlerForUnknownDeviceType() {
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);

        Thing thing = unknownDeviceThing();
        ThingHandler handler = vicareHandlerFactory.createHandler(thing);
        assertTrue(handler instanceof VicareDeviceThingHandler);
    }

    @Test
    public void supportsUnknownDeviceThingType() {
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);

        Thing thing = unknownDeviceThing();

        assertTrue(vicareHandlerFactory.supportsThingType(thing.getThingTypeUID()));
    }

    private Thing unknownDeviceThing() {
        Thing thing = mock(Thing.class);
        when(thing.getThingTypeUID()).thenReturn(UNKNOWN_DEVICE_THING_TYPE);
        return thing;
    }
}
