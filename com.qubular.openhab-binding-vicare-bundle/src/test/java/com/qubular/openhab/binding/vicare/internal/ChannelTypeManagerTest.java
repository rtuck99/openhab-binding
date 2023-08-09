package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.channeltype.ChannelTypeManager;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Unit;
import com.qubular.vicare.model.features.NumericSensorFeature;
import com.qubular.vicare.model.features.StatusSensorFeature;
import com.qubular.vicare.model.values.DimensionalValue;
import com.qubular.vicare.model.values.StatusValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeUID;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.encodeThingId;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.encodeThingUniqueId;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ChannelTypeManagerTest {
    public static final long INSTALLATION_ID = 1234L;
    public static final String GATEWAY_SERIAL = "SERIAL_1234";
    public static final String DEVICE_1_ID = "0";
    public static final ThingUID THING_UID_BRIDGE = new ThingUID(THING_TYPE_BRIDGE, UUID.randomUUID().toString());
    private static final ThingUID DEVICE_THING_ID = new ThingUID(THING_TYPE_HEATING, THING_UID_BRIDGE, encodeThingId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID));
    private VicareServiceProvider vicareServiceProvider;
    private ThingRegistry thingRegistry;
    private VicareChannelTypeProvider channelTypeProvider;
    private FeatureService featureService;

    @BeforeEach()
    public void setUp() {
        vicareServiceProvider = mock(VicareServiceProvider.class);
        MockChannelTypeRegistry channelTypeRegistry = new MockChannelTypeRegistry();
        thingRegistry = mock(ThingRegistry.class);
        channelTypeProvider = mock(VicareChannelTypeProvider.class);
        channelTypeRegistry.addChannelTypeProvider(new XmlChannelTypeProvider());
        channelTypeRegistry.addChannelTypeProvider(channelTypeProvider);
        featureService = mock(FeatureService.class);
        when(vicareServiceProvider.getThingRegistry()).thenReturn(thingRegistry);
        when(vicareServiceProvider.getChannelTypeRegistry()).thenReturn(channelTypeRegistry);
        when(vicareServiceProvider.getChannelTypeProvider()).thenReturn(channelTypeProvider);
        when(vicareServiceProvider.getFeatureService()).thenReturn(featureService);
    }

    @Test
    public void managerEnumeratesChannelTypes_atStartup() {
        Thing thing = thingWithSomeChannels();

        ChannelTypeManager channelTypeManager = new ChannelTypeManager(vicareServiceProvider);

        var channelTypeCaptor = ArgumentCaptor.forClass(ChannelType.class);
        verify(channelTypeProvider, timeout(10000)).addChannelType(channelTypeCaptor.capture());

        ChannelType channelType = channelTypeCaptor.getValue();
        assertNotNull(channelType);
        assertEquals("Heating Circuit 0 Circulation Pump Status", channelType.getLabel());
        assertEquals("Shows the state of the circulation pump (on, off) for heating circuit 0 (read-only)", channelType.getDescription());
        assertEquals(true, channelType.getState().isReadOnly());
        assertEquals("String", channelType.getItemType());
    }

    @Test
    public void managerEnumeratesChannelTypes_onThingAdded() {
        ChannelTypeManager channelTypeManager = new ChannelTypeManager(vicareServiceProvider);

        var listenerCaptor = ArgumentCaptor.forClass(ThingRegistryChangeListener.class);
        verify(thingRegistry).addRegistryChangeListener(listenerCaptor.capture());

        listenerCaptor.getValue().added(thingWithSomeChannels());

        var channelTypeCaptor = ArgumentCaptor.forClass(ChannelType.class);
        verify(channelTypeProvider).addChannelType(channelTypeCaptor.capture());

        ChannelType channelType = channelTypeCaptor.getValue();
        assertNotNull(channelType);
        assertEquals("Heating Circuit 0 Circulation Pump Status", channelType.getLabel());
        assertEquals("Shows the state of the circulation pump (on, off) for heating circuit 0 (read-only)", channelType.getDescription());
        assertEquals(true, channelType.getState().isReadOnly());
        assertEquals("String", channelType.getItemType());
    }

    private Thing thingWithSomeChannels() {
        Thing thing = mock(Thing.class);
        when(thing.getConfiguration()).thenReturn(new Configuration());
        when(thing.getUID()).thenReturn(DEVICE_THING_ID);

        ChannelTypeUID dhwSensorsTemperatureOutletChannelTypeUID = new ChannelTypeUID(BINDING_ID, "heating_dhw_sensors_temperature_outlet");
        Channel dhwSensorsTemperatureOutletChannel = ChannelBuilder.create(new ChannelUID(thing.getUID(), "heating_dhw_sensors_temperature_outlet"), "Number:Temperature")
                .withType(dhwSensorsTemperatureOutletChannelTypeUID)
                .withProperties(Map.of(PROPERTY_FEATURE_NAME, "heating.dhw.sensors.temperature.outlet",
                                       PROPERTY_PROP_NAME, "value",
                                       "0", "outlet"))
                .build();

        Feature dhwSensorsTemperatureOutletFeature = new NumericSensorFeature("heating.dhw.sensors.temperature.outlet", "value",
                                                             new DimensionalValue(new Unit("celsius"), 27.3), new StatusValue("connected"), null
        );
        when(featureService.getFeature(same(thing), eq("heating.dhw.sensors.temperature.outlet"), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(dhwSensorsTemperatureOutletFeature)));

        ChannelTypeUID heatingBurnersStatisticsChannelTypeUID = new ChannelTypeUID(BINDING_ID, "heating_burners_statistics_starts");
        Channel heatingBurners0StatisticsStartsChannel = ChannelBuilder.create(new ChannelUID(thing.getUID(), "heating_burners_0_statistics_starts"), "Number")
                .withType(heatingBurnersStatisticsChannelTypeUID)
                .withProperties(Map.of(PROPERTY_FEATURE_NAME, "heating.burners.0.statistics",
                                       PROPERTY_PROP_NAME, "starts",
                                       "heatingCircuit", "0"))
                .build();
        Feature heatingBurners0StatisticsFeature = new StatusSensorFeature("heating.burners.0.statistics",
                                                                           Map.of("starts", new DimensionalValue(Unit.EMPTY, 312),
                                                                                      "hours", new DimensionalValue(Unit.HOUR, 5.0)),
                                                                           emptyList());
        when(featureService.getFeature(same(thing), eq("heating.burners.0.statistics"), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(heatingBurners0StatisticsFeature)));


        ChannelTypeUID heatingCircuits0CirculationPumpStatusChannelTypeUID = new ChannelTypeUID(BINDING_ID, "heating_circuits_0_circulation_pump_status");
        Channel heatingCircuits0CirculationPumpStatusChannel = ChannelBuilder.create(new ChannelUID(thing.getUID(), "heating_circuits_0_circulation_pump_status"), "Number")
                .withType(heatingCircuits0CirculationPumpStatusChannelTypeUID)
                .withProperties(Map.of(PROPERTY_FEATURE_NAME, "heating.circuits.0.circulation.pump",
                                       PROPERTY_PROP_NAME, "status",
                                       "heatingCircuit", "0"))
                .build();
        Feature heatingCircuits0CirculationPumpFeature = new StatusSensorFeature("heating.circuits.0.circulation.pump",
                                                                             new StatusValue("on"),
                                                                             null);
        when(featureService.getFeature(same(thing), eq("heating.circuits.0.circulation.pump"), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(heatingCircuits0CirculationPumpFeature)));

        when(thing.getChannels()).thenReturn(List.of(dhwSensorsTemperatureOutletChannel,
                                                     heatingBurners0StatisticsStartsChannel,
                                                     heatingCircuits0CirculationPumpStatusChannel));

        when(thingRegistry.getAll()).thenReturn(singletonList(thing));
        when(thingRegistry.get(DEVICE_THING_ID)).thenReturn(thing);
        Map<String, String> propMap = new HashMap<>(Map.of(PROPERTY_DEVICE_UNIQUE_ID, encodeThingUniqueId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID)));
        doReturn(propMap).when(thing).getProperties();

        return thing;
    }
}
