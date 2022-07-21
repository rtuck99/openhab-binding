package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.*;
import com.qubular.vicare.model.features.NumericSensorFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.core.config.discovery.DiscoveryListener;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.encodeThingId;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.encodeThingUniqueId;
import static com.qubular.vicare.model.Device.DEVICE_TYPE_HEATING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VicareBindingTest {
    public static final ThingUID THING_UID_BRIDGE = new ThingUID(THING_TYPE_BRIDGE, UUID.randomUUID().toString());
    public static final long INSTALLATION_ID = 1234L;
    public static final String GATEWAY_SERIAL = "SERIAL_1234";
    public static final String DEVICE_ID = "0";
    @Mock
    private VicareService vicareService;

    @Mock
    private ThingRegistry thingRegistry;
    private ComponentContext componentContext;
    private BundleContext bundleContext;

    private void simpleHeatingInstallation() throws AuthenticationException, IOException {
        Installation installation = mock(Installation.class);
        Gateway gateway = mock(Gateway.class);
        doReturn(GATEWAY_SERIAL).when(gateway).getSerial();
        Device device = mock(Device.class);
        doReturn(DEVICE_ID).when(device).getId();
        doReturn(DEVICE_TYPE_HEATING).when(device).getDeviceType();
        doReturn(List.of(device)).when(gateway).getDevices();
        doReturn(List.of(gateway)).when(installation).getGateways();
        doReturn(INSTALLATION_ID).when(installation).getId();
        List<Installation> installations = List.of(installation);
        doReturn(installations).when(vicareService).getInstallations();

        Feature temperatureSensor = new NumericSensorFeature("heating.dhw.sensors.temperature.outlet", new DimensionalValue(new Unit("celsius"), 27.3), new Status("connected"));
        when(vicareService.getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_ID))
                .thenReturn(List.of(temperatureSensor));

    }

    private Bridge vicareBridge() {
        Bridge bridge = mock(Bridge.class);
        doReturn(THING_UID_BRIDGE).when(bridge).getUID();
        doReturn(THING_TYPE_BRIDGE).when(bridge).getThingTypeUID();
        return bridge;
    }

    @BeforeEach
    void setUp() {
        componentContext = mock(ComponentContext.class);
        bundleContext = mock(BundleContext.class);
        doReturn(bundleContext).when(componentContext).getBundleContext();
    }

    @Test
    public void registeringABridgeStartsAScan() throws AuthenticationException, IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();

        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(thingRegistry, vicareService);
        activateHandlerFactory(vicareHandlerFactory);
        ArgumentCaptor<ThingRegistryChangeListener> listenerCaptor = forClass(ThingRegistryChangeListener.class);
        verify(thingRegistry).addRegistryChangeListener(listenerCaptor.capture());
        listenerCaptor.getValue().added(bridge);

        ArgumentCaptor<VicareDiscoveryService> discoveryServiceCaptor = forClass(VicareDiscoveryService.class);
        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        verify(bundleContext).registerService(eq(DiscoveryService.class.getName()), discoveryServiceCaptor.capture(), any(Dictionary.class));
        VicareDiscoveryService discoveryService = discoveryServiceCaptor.getValue();

        discoveryService.addDiscoveryListener(discoveryListener);
        verify(vicareService, timeout(1000)).getInstallations();
        ArgumentCaptor<DiscoveryResult> resultArgumentCaptor = forClass(DiscoveryResult.class);
        verify(discoveryListener, timeout(1000)).thingDiscovered(same(discoveryService), resultArgumentCaptor.capture());
        DiscoveryResult result = resultArgumentCaptor.getValue();
        assertEquals("vicare:heating:" + THING_UID_BRIDGE.getId() + ":0328bf05-9b58-35fe-9845-edfc5a9b09aa", result.getThingUID().getAsString());
        assertEquals(THING_UID_BRIDGE, result.getBridgeUID());
        assertEquals(encodeThingUniqueId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_ID), result.getProperties().get("deviceUniqueId"));
        assertEquals("deviceUniqueId", result.getRepresentationProperty());
    }

    @Test
    public void handlerFactoryCreateDeviceThingHandler() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        vicareBridge();

        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(thingRegistry, vicareService);

        Thing deviceThing = heatingDeviceThing();

        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);

        assertNotNull(handler);
    }

    @Test
    public void initializeDeviceHandlerCreatesTemperatureSensor() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        VicareBridgeHandler bridgeHandler = new VicareBridgeHandler(vicareService, thingRegistry, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn(bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(thingRegistry, vicareService);
        Thing deviceThing = heatingDeviceThing();
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        when(callback.getBridge(THING_UID_BRIDGE)).thenReturn(bridge);
        handler.setCallback(callback);


        doAnswer(invocation -> {
            Thing thing = invocation.getArgument(0);
            doReturn(thing).when(thingRegistry).get(thing.getUID());
            return null;
        }).when(callback).thingUpdated(any(Thing.class));
        handler.initialize();
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000)).thingUpdated(thingCaptor.capture());
        Channel channel = thingCaptor.getValue().getChannel(new ChannelUID(deviceThing.getUID(), "heating_dhw_sensors_temperature_outlet"));
        assertNotNull(channel);
        assertEquals("heating_dhw_sensors_temperature_outlet", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.outlet", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(27.3, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);
    }

    private Thing heatingDeviceThing() {
        Thing deviceThing = mock(Thing.class);
        doReturn(THING_UID_BRIDGE).when(deviceThing).getBridgeUID();
        doReturn(THING_TYPE_HEATING).when(deviceThing).getThingTypeUID();
        ThingUID deviceThingId = new ThingUID(THING_TYPE_HEATING, THING_UID_BRIDGE, encodeThingId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_ID));
        doReturn(deviceThingId).when(deviceThing).getUID();
        doReturn(Map.of(PROPERTY_DEVICE_UNIQUE_ID, encodeThingUniqueId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_ID))).when(deviceThing).getProperties();
        doReturn(THING_UID_BRIDGE).when(deviceThing).getBridgeUID();
        doReturn(deviceThing).when(thingRegistry).get(deviceThingId);
        return deviceThing;
    }

    private void activateHandlerFactory(VicareHandlerFactory vicareHandlerFactory) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method activate = BaseThingHandlerFactory.class.getDeclaredMethod("activate", ComponentContext.class);
        activate.setAccessible(true);
        activate.invoke(vicareHandlerFactory, componentContext);
    }

}
