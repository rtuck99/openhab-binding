package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.thingtype.VicareThingTypeProvider;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Gateway;
import com.qubular.vicare.model.Installation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.core.config.discovery.DiscoveryListener;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.type.ThingTypeRegistry;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.util.List;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.BINDING_ID;
import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_DEVICE_UNIQUE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VicareDiscoveryServiceTest {
    public static final long INSTALLATION_ID = 123456L;
    public static final String GATEWAY_SERIAL = "1234567";
    public static final String DEVICE_ID = "001";
    private VicareBridgeHandler bridgeHandler;
    @Mock
    private BundleContext bundleContext;
    @Mock
    private VicareService vicareService;
    @Mock
    private Bridge bridge;
    @Mock
    private VicareServiceProvider vicareServiceProvider;
    @Mock
    private ThingTypeRegistry thingTypeRegistry;
    @Mock
    private VicareThingTypeProvider vicareThingTypeProvider;

    private AutoCloseable mocks;
    private ThingUID bridgeId;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        bridgeHandler = mock(VicareBridgeHandler.class, withSettings().extraInterfaces(VicareThingHandler.class));
        doReturn(bridge).when(bridgeHandler).getThing();
        bridgeId = new ThingUID(BINDING_ID, "bridgeId");
        doReturn(bridgeId).when(bridge).getUID();
        doReturn(thingTypeRegistry).when(vicareServiceProvider).getThingTypeRegistry();
        doReturn(vicareThingTypeProvider).when(vicareServiceProvider).getVicareThingTypeProvider();
        doReturn(vicareServiceProvider).when(bridgeHandler).getVicareServiceProvider();
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void discoveryWhenNullProperties() throws AuthenticationException, IOException {
        doReturn(vicareService).when((VicareThingHandler)bridgeHandler).getVicareService();
        List<Device> devices = List.of(new Device(GATEWAY_SERIAL,
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  Device.DEVICE_TYPE_HEATING));
        List<Gateway> gateways = List.of(new Gateway(GATEWAY_SERIAL,
                                                     null,
                                                     0,
                                                     null,
                                                     null,
                                                     null,
                                                     INSTALLATION_ID,
                                                     devices));
        List<Installation> installations = List.of(new Installation(123456L, "Test description",
                                                                    gateways,
                                                                    null));
        doReturn(installations).when(vicareService).getInstallations();

        VicareDiscoveryService discoveryService = new VicareDiscoveryService();
        discoveryService.setThingHandler(bridgeHandler);
        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        discoveryService.addDiscoveryListener(discoveryListener);

        ArgumentCaptor<DiscoveryResult> discoveryCaptor = ArgumentCaptor.forClass(DiscoveryResult.class);
        verify(discoveryListener, timeout(1000))
                .thingDiscovered(same(discoveryService), discoveryCaptor.capture());
    }

    @Test
    public void discoversUnrecognisedDeviceTypes() throws AuthenticationException, IOException {
        doReturn(vicareService).when((VicareThingHandler)bridgeHandler).getVicareService();
        List<Installation> installations = gatewayWithUnknownAttachedDevice();

        VicareDiscoveryService discoveryService = new VicareDiscoveryService();
        discoveryService.setThingHandler(bridgeHandler);
        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        discoveryService.addDiscoveryListener(discoveryListener);

        ArgumentCaptor<DiscoveryResult> discoveryCaptor = ArgumentCaptor.forClass(DiscoveryResult.class);
        verify(discoveryListener, timeout(1000))
                .thingDiscovered(same(discoveryService), discoveryCaptor.capture());

        assertEquals("Unrecognised unknown_device_type device", discoveryCaptor.getValue().getLabel());
        assertEquals(bridgeId, discoveryCaptor.getValue().getBridgeUID());
        assertEquals(PROPERTY_DEVICE_UNIQUE_ID, discoveryCaptor.getValue().getRepresentationProperty());
        ThingTypeUID thingTypeUID = new ThingTypeUID("vicare:unknown_device_type");
        assertEquals(new ThingUID(thingTypeUID, bridgeId, VicareUtil.encodeThingId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_ID)), discoveryCaptor.getValue().getThingUID());
    }

    private List<Installation> gatewayWithUnknownAttachedDevice() throws AuthenticationException, IOException {
        List<Device> devices = List.of(new Device(GATEWAY_SERIAL,
                                                  DEVICE_ID,
                                                  null,
                                                  null,
                                                  null,
                                                  "unknown_device_type"));
        List<Gateway> gateways = List.of(new Gateway(GATEWAY_SERIAL,
                                                     null,
                                                     0,
                                                     null,
                                                     null,
                                                     null,
                                                     INSTALLATION_ID,
                                                     devices));
        List<Installation> installations = List.of(new Installation(123456L, "Test description",
                                                                    gateways,
                                                                    null));
        doReturn(installations).when(vicareService).getInstallations();
        return installations;
    }

}