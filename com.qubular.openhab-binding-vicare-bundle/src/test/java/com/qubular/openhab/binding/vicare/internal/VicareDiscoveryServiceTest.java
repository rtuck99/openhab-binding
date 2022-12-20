package com.qubular.openhab.binding.vicare.internal;

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
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BridgeHandler;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.util.List;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.BINDING_ID;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VicareDiscoveryServiceTest {
    private BridgeHandler bridgeHandler;
    @Mock
    private BundleContext bundleContext;
    @Mock
    private VicareService vicareService;
    @Mock
    private Bridge bridge;

    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        bridgeHandler = mock(VicareBridgeHandler.class, withSettings().extraInterfaces(VicareThingHandler.class));
        doReturn(bridge).when(bridgeHandler).getThing();
        doReturn(new ThingUID(BINDING_ID, "bridgeId")).when(bridge).getUID();
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void discoveryWhenNullProperties() throws AuthenticationException, IOException {
        doReturn(vicareService).when((VicareThingHandler)bridgeHandler).getVicareService();
        List<Device> devices = List.of(new Device("1234567",
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  Device.DEVICE_TYPE_HEATING));
        List<Gateway> gateways = List.of(new Gateway("1234567",
                                                     null,
                                                     0,
                                                     null,
                                                     null,
                                                     null,
                                                     123456L,
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
}