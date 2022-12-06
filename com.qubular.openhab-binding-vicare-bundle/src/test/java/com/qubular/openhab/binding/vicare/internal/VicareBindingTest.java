package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.configuration.SimpleConfiguration;
import com.qubular.openhab.binding.vicare.internal.tokenstore.PersistedTokenStore;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.CommandFailureException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.*;
import com.qubular.vicare.model.features.*;
import com.qubular.vicare.model.params.EnumParamDescriptor;
import com.qubular.vicare.model.params.NumericParamDescriptor;
import com.qubular.vicare.model.values.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryListener;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.library.types.*;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.*;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.*;
import org.openhab.core.types.*;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.encodeThingId;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.encodeThingUniqueId;
import static com.qubular.vicare.model.Device.DEVICE_TYPE_HEATING;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VicareBindingTest {
    public static final ThingUID THING_UID_BRIDGE = new ThingUID(THING_TYPE_BRIDGE, UUID.randomUUID().toString());
    public static final long INSTALLATION_ID = 1234L;
    public static final String GATEWAY_SERIAL = "SERIAL_1234";
    public static final String DEVICE_1_ID = "0";
    public static final String DEVICE_2_ID = "1";
    public static final URI SET_MODE_URI = URI.create(
            "https://api.viessmann.com/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.modes.active/commands/setMode");

    public static final URI SET_LEVEL_MIN_URI = URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.circuits.0.temperature.levels/commands/setMin");
    public static final URI SET_LEVEL_MAX_URI = URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.circuits.0.temperature.levels/commands/setMax");
    public static final URI SET_LEVEL_LEVEL_URI = URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.circuits.0.temperature.levels/commands/setLevels");

    @Mock
    private VicareService vicareService;
    private SimpleConfiguration configuration;
    @Mock
    private ThingRegistry thingRegistry;
    @Mock
    private ConfigurationAdmin configurationAdmin;
    @Mock
    private VicareServiceProvider vicareServiceProvider;
    private MyChannelTypeRegistry channelTypeRegistry;
    @Mock
    private ChannelTypeProvider xmlChannelTypeProvider;

    private ComponentContext componentContext;
    private BundleContext bundleContext;

    private ThingHandler bridgeHandler;

    private static Map<ChannelTypeUID, ChannelType> channelTypes;


    private void simpleHeatingInstallation() throws AuthenticationException, IOException {
        Installation installation = mock(Installation.class);
        Gateway gateway = mock(Gateway.class);
        doReturn(GATEWAY_SERIAL).when(gateway).getSerial();
        Device device = mock(Device.class);
        doReturn(DEVICE_1_ID).when(device).getId();
        doReturn(DEVICE_TYPE_HEATING).when(device).getDeviceType();
        doReturn("1234567890123245").when(device).getBoilerSerial();
        doReturn(GATEWAY_SERIAL).when(device).getGatewaySerial();
        doReturn("E3_Vitodens_100_0421").when(device).getModelId();
        doReturn("Online").when(device).getStatus();
        doReturn(List.of(device)).when(gateway).getDevices();
        doReturn(List.of(gateway)).when(installation).getGateways();
        doReturn(INSTALLATION_ID).when(installation).getId();
        List<Installation> installations = List.of(installation);
        doReturn(installations).when(vicareService).getInstallations();

        Feature temperatureSensor = new NumericSensorFeature("heating.dhw.sensors.temperature.outlet", "value",
                                                             new DimensionalValue(new Unit("celsius"), 27.3), new StatusValue("connected"), null
        );
        Feature statisticsFeature = new StatusSensorFeature("heating.burners.0.statistics",
                                                          Map.of("starts", new DimensionalValue(new Unit("starts"), 5.0))
        );
        Feature textFeature = new TextFeature("device.serial", "value", "7723181102527121");
        Feature statusFeature = new StatusSensorFeature("heating.circuits.0.circulation.pump", new StatusValue("on"), null);
        List<ParamDescriptor> params = List.of(new NumericParamDescriptor(true, "targetTemperature", 3.0, 37.0, 1.0));
        List<Feature> programFeatures = List.of(new NumericSensorFeature("heating.circuits.0.operating.programs.normal",
                                                                "temperature",
                                                                List.of(new CommandDescriptor("setTemperature", true, params, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normal/commands/setTemperature"))),
                                                                new DimensionalValue(new Unit("celsius"), 21), StatusValue.NA, true),
                                                new NumericSensorFeature("heating.circuits.0.operating.programs.reduced",
                                                                "temperature",
                                                                List.of(new CommandDescriptor("setTemperature", true, params, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.reduced/commands/setTemperature"))),
                                                                new DimensionalValue(new Unit("celsius"), 12), StatusValue.NA, false),
                                                new NumericSensorFeature("heating.circuits.0.operating.programs.comfort",
                                                                "temperature",
                                                                List.of(new CommandDescriptor("setTemperature", true, params, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfort/commands/setTemperature"))),
                                                                new DimensionalValue(new Unit("celsius"), 22), StatusValue.NA, false),
                                                new NumericSensorFeature("heating.circuits.0.operating.programs.reducedHeating",
                                                                "temperature",
                                                                List.of(new CommandDescriptor("setTemperature", true, params, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.reducedHeating/commands/setTemperature"))),
                                                                new DimensionalValue(new Unit("celsius"), 18), StatusValue.NA, false),
                                                new NumericSensorFeature("heating.circuits.0.operating.programs.normalHeating",
                                                                "temperature",
                                                                List.of(new CommandDescriptor("setTemperature", true, params, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normalHeating/commands/setTemperature"))),
                                                                new DimensionalValue(new Unit("celsius"), 21), StatusValue.NA, true),
                                                new NumericSensorFeature("heating.circuits.0.operating.programs.comfortHeating",
                                                                "temperature",
                                                                List.of(new CommandDescriptor("setTemperature", true, params, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfortHeating/commands/setTemperature"))),
                                                                new DimensionalValue(new Unit("celsius"), 22), StatusValue.NA, false),
                                                new NumericSensorFeature("heating.circuits.0.operating.programs.eco",
                                                                "temperature",
                                                                new DimensionalValue(new Unit("celsius"), 21), StatusValue.NA, false),
                                                new NumericSensorFeature("heating.circuits.0.operating.programs.external",
                                                                "temperature",
                                                                new DimensionalValue(new Unit("celsius"), 0), StatusValue.NA, false)

        );
        Feature consumptionFeature = new ConsumptionSummaryFeature("heating.gas.consumption.summary.dhw",
                new DimensionalValue(new Unit("cubicMeter"), 0.2),
                new DimensionalValue(new Unit("cubicMeter"), 2.1),
                new DimensionalValue(new Unit("cubicMeter"), 1.8),
                new DimensionalValue(new Unit("cubicMeter"), 5.9));
        Feature burnerFeature = new StatusSensorFeature("heating.burners.0", StatusValue.NA, true);
        Feature curveFeature = new CurveFeature("heating.circuits.0.heating.curve",
                new DimensionalValue(new Unit(""), 1.6),
                new DimensionalValue(new Unit(""), -4.0));
        Feature holidayFeature = new DatePeriodFeature("heating.circuits.0.operating.programs.holiday",
                true,
                LocalDate.parse("2022-12-23"),
                LocalDate.parse("2022-12-26"));
        Feature heatingDhw = new StatusSensorFeature("heating.dhw", StatusValue.ON, true);
        Feature heatingDhwTemperatureHotWaterStorage = new NumericSensorFeature("heating.dhw.sensors.temperature.hotWaterStorage",
                                                                                "value",
                                                                                new DimensionalValue(new Unit("celsius"), 54.3), new StatusValue("connected"), null
        );
        Feature operatingModesActive = new TextFeature("heating.circuits.0.operating.modes.active",
                                                       "value",
                                                       "dhw",
                                                     List.of(new CommandDescriptor("setMode", true,
                                                                                   List.of(new EnumParamDescriptor(true, "mode",
                                                                                                                   Set.of("standby", "heating", "dhw", "dhwAndHeating"))),
                                                                                   SET_MODE_URI)));
        Feature heatingCircuitTemperatureLevels = new StatusSensorFeature("heating.circuits.0.temperature.levels",
                                                                        Map.of("min", new DimensionalValue(Unit.CELSIUS, 20.0),
                                                                               "max", new DimensionalValue(Unit.CELSIUS, 45.0)),
                                                                        List.of(new CommandDescriptor("setMin", false, List.of(new NumericParamDescriptor(true, "temperature", 20.0, 20.0, 1.0)), SET_LEVEL_MIN_URI),
                                                                                new CommandDescriptor("setMax", true, List.of(new NumericParamDescriptor(true, "temperature", 21.0, 80.0, 1.0)), SET_LEVEL_MAX_URI),
                                                                                new CommandDescriptor("setLevels", true, List.of(new NumericParamDescriptor(true, "min", 20.0, 20.0, 1.0), new NumericParamDescriptor(true, "max", 21.0, 80.0, 1.0)), SET_LEVEL_LEVEL_URI))
        );
        Feature heatingDhwTemperatureMain = new NumericSensorFeature("heating.dhw.temperature.main",
                                                                     "value",
                                                                     List.of(new CommandDescriptor("setTargetTemperature",
                                                                                                   true,
                                                                                                   List.of(new NumericParamDescriptor(true,
                                                                                                                                      "temperature",
                                                                                                                                      30.0,
                                                                                                                                      60.0,
                                                                                                                                      1.0)),
                                                                                                   URI.create("https://api.viessmann.com/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.dhw.temperature.main/commands/setTargetTemperature"))),
                                                                     new DimensionalValue(Unit.CELSIUS, 50.0),
                                                                     StatusValue.NA,
                                                                     null);
        Feature solarSensorsTemperatureCollector = new NumericSensorFeature("heating.solar.sensors.temperature.collector",
                                                                            "value",
                                                                            new DimensionalValue(Unit.CELSIUS, 35.4),
                                                                            new StatusValue("connected"),
                                                                            null);
        Feature solarPowerProduction = new ConsumptionTotalFeature("heating.solar.power.production",
                                                                   Map.of("day", new ArrayValue(Unit.KILOWATT_HOUR, new double[]{0.0, 11.4, 7.3, 12.3, 27.3, 12.6, 2.9, 2.3}),
                                                                          "week", new ArrayValue(Unit.KILOWATT_HOUR, new double[]{31.0, 58.3, 147.2, 44.5, 149.6, 21.9, 84.7}),
                                                                          "month", new ArrayValue(Unit.KILOWATT_HOUR, new double[]{247.8, 355.5, 419.6, 437.9, 383.9, 512, 634.5, 905.1, 296.6, 57.7, 109.6, 162.7, 490.3}),
                                                                          "year", new ArrayValue(Unit.KILOWATT_HOUR, new double[]{4250.6, 0})));
        Feature solarSensorsTemperatureDHW = new NumericSensorFeature("heating.solar.sensors.temperature.dhw",
                                                                      "value",
                                                                      new DimensionalValue(Unit.CELSIUS, 28.1),
                                                                      new StatusValue("connected"),
                                                                      null);
        Feature solarPumpsCircuit = new StatusSensorFeature("heating.solar.pumps.circuit",
                                                            StatusValue.OFF,
                                                            null);
        Feature heatingSolar = new StatusSensorFeature("heating.solar",
                                                        StatusValue.NA,
                                                        true);
        Feature heatingCircuit = new TextFeature("heating.circuits.0",
                                                     "name",
                                                     "circuitName", emptyList());
        Feature heatingCircuitName = new TextFeature("heating.circuits.0.name",
                                                     "name",
                                                     "circuitNameName", emptyList());
        Feature operatingModesDHW = new StatusSensorFeature("heating.circuits.1.operating.modes.dhw",
                                                            StatusValue.NA,
                                                            false);
        Feature operatingModesDHWAndHeating = new StatusSensorFeature("heating.circuits.1.operating.modes.dhwAndHeating",
                                                                      StatusValue.NA,
                                                                      true);
        Feature operatingModesHeating = new StatusSensorFeature("heating.circuits.1.operating.modes.heating",
                                                                StatusValue.NA,
                                                                false);
        Feature operatingModesStandby = new StatusSensorFeature("heating.circuits.1.operating.modes.standby",
                                                                StatusValue.NA,
                                                                false);
        Feature heatingCircuitsOperatingProgramsForcedLastFromSchedule = new StatusSensorFeature("heating.circuits.1.operating.programs.forcedLastFromSchedule",
                                                                                                 StatusValue.NA,
                                                                                                 false);
        List<Feature> operatingProgramsStatusSensorFeatures = List.of(
            new StatusSensorFeature("heating.circuits.1.operating.programs.standby",
                                                                                          StatusValue.NA,
                                                                                          false),
            new StatusSensorFeature("heating.circuits.1.operating.programs.summerEco",
                                                                                        StatusValue.NA,
                                                                                        false),
            new StatusSensorFeature("heating.circuits.1.operating.programs.fixed",
                                                                                        StatusValue.NA,
                                                                                        false),
            new StatusSensorFeature("heating.circuits.1.operating.programs.normalEnergySaving",
                                                                                              Map.of("active", BooleanValue.valueOf(false),
                                                                                                     "reason", new StringValue("summerEco"),
                                                                                                     "demand", new StringValue("heating"))),
            new StatusSensorFeature("heating.circuits.1.operating.programs.reducedEnergySaving",
                                                                                              Map.of("active", BooleanValue.valueOf(false),
                                                                                                     "reason", new StringValue("unknown"),
                                                                                                     "demand", new StringValue("heating"))),
            new StatusSensorFeature("heating.circuits.1.operating.programs.comfortEnergySaving",
                                                                                              Map.of("active", BooleanValue.valueOf(false),
                                                                                                     "reason", new StringValue("summerEco"),
                                                                                                     "demand", new StringValue("heating"))),
            new StatusSensorFeature("heating.circuits.1.operating.programs.normalCoolingEnergySaving",
                                                                                              Map.of("active", BooleanValue.valueOf(false),
                                                                                                     "reason", new StringValue("summerEco"),
                                                                                                     "demand", new StringValue("cooling"))),
            new StatusSensorFeature("heating.circuits.1.operating.programs.reducedCoolingEnergySaving",
                                                                                              Map.of("active", BooleanValue.valueOf(false),
                                                                                                     "reason", new StringValue("summerEco"),
                                                                                                     "demand", new StringValue("cooling"))),
            new StatusSensorFeature("heating.circuits.1.operating.programs.comfortCoolingEnergySaving",
                                                                                              Map.of("active", BooleanValue.valueOf(false),
                                                                                                     "reason", new StringValue("summerEco"),
                                                                                                     "demand", new StringValue("cooling")))
        );
        Feature heatingCircuitsSensorsTemperatureSupply = new NumericSensorFeature("heating.circuits.1.sensors.temperature.supply",
                                                                                   "value",
                                                                                   new DimensionalValue(Unit.CELSIUS, 24.6),
                                                                                   new StatusValue("connected"),
                                                                                   false);
        Feature heatingCircuitsZoneMode = new StatusSensorFeature("heating.circuits.1.zone.mode",
                                                                  StatusValue.NA,
                                                                  false);
        Feature heatingDHWHygiene = new StatusSensorFeature("heating.dhw.hygiene",
                                                            Map.of("enabled", BooleanValue.FALSE));
        Feature heatingDHWOneTimeCharge = new StatusSensorFeature("heating.dhw.oneTimeCharge",
                                                                  Map.of("active", BooleanValue.FALSE),
                                                                  List.of(new CommandDescriptor("activate", true, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/activate")),
                                                                          new CommandDescriptor("deactivate", false, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/deactivate"))));
        Feature heatingDHWOperatingModesOff = new StatusSensorFeature("heating.dhw.operating.modes.off",
                StatusValue.NA,
                false);
        List<Feature> features = new ArrayList<>();
        features.addAll(programFeatures);
        features.addAll(operatingProgramsStatusSensorFeatures);
        features.addAll(List.of(temperatureSensor, statisticsFeature, textFeature, statusFeature,
                                                  consumptionFeature, burnerFeature, curveFeature, holidayFeature,
                                                  heatingDhw,
                                                  heatingDhwTemperatureHotWaterStorage, operatingModesActive,
                                                  heatingCircuitTemperatureLevels,
                                                  heatingDhwTemperatureMain,
                                                  solarSensorsTemperatureCollector,
                                                  solarPowerProduction,
                                                  solarSensorsTemperatureDHW,
                                                  solarPumpsCircuit,
                                                  heatingSolar,
                                                  heatingCircuit,
                                                  heatingCircuitName,
                                                  operatingModesDHW,
                                                  operatingModesDHWAndHeating,
                                                  operatingModesHeating,
                                                  operatingModesStandby,
                                                  heatingCircuitsOperatingProgramsForcedLastFromSchedule,
                                                  heatingCircuitsSensorsTemperatureSupply,
                                                  heatingCircuitsZoneMode,
                                                  heatingDHWHygiene,
                                                  heatingDHWOneTimeCharge,
                                                  heatingDHWOperatingModesOff));
        doReturn(
                features)
                .when(vicareService)
                .getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
    }

    private void oneHeatingInstallationTwoBoilers() throws AuthenticationException, IOException {
        Installation installation = mock(Installation.class);
        Gateway gateway = mock(Gateway.class);
        doReturn(GATEWAY_SERIAL).when(gateway).getSerial();
        Device device1 = mock(Device.class);
        doReturn(DEVICE_1_ID).when(device1).getId();
        doReturn(DEVICE_TYPE_HEATING).when(device1).getDeviceType();
        Device device2 = mock(Device.class);
        doReturn(DEVICE_2_ID).when(device2).getId();
        doReturn(DEVICE_TYPE_HEATING).when(device2).getDeviceType();
        doReturn(List.of(device1, device2)).when(gateway).getDevices();
        doReturn(List.of(gateway)).when(installation).getGateways();
        doReturn(INSTALLATION_ID).when(installation).getId();
        List<Installation> installations = List.of(installation);
        doReturn(installations).when(vicareService).getInstallations();
        Feature textFeature1 = new TextFeature("device.serial", "value", "1111111111111111");
        Feature textFeature2 = new TextFeature("device.serial", "value", "2222222222222222");
        when(vicareService.getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID))
                .thenReturn(List.of(textFeature1));
        when(vicareService.getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_2_ID))
                .thenReturn(List.of(textFeature2));
    }

    private Bridge vicareBridge() {
        Bridge bridge = mock(Bridge.class);
        doReturn(THING_UID_BRIDGE).when(bridge).getUID();
        doReturn(THING_TYPE_BRIDGE).when(bridge).getThingTypeUID();
        Configuration openhabConfig = mock(Configuration.class);
        doReturn(openhabConfig).when(bridge).getConfiguration();
        doReturn(Map.of("clientId", "myClientId",
                "accessServerUri", "http://localhost:9000/access",
                "iotServerUri", "http://localhost:9000/iot"))
                .when(openhabConfig).getProperties();
        return bridge;
    }

    @BeforeAll
    static void setUpClass() {
        channelTypes = readChannelTypes();
    }

    @BeforeEach
    void setUp() throws IOException {
        componentContext = mock(ComponentContext.class);
        bundleContext = mock(BundleContext.class);
        configuration = new SimpleConfiguration(bundleContext);
        var myChannelTypeRegistry = new MyChannelTypeRegistry();
        this.channelTypeRegistry = myChannelTypeRegistry;
        doReturn(bundleContext).when(componentContext).getBundleContext();
        Bundle bundle = mock(Bundle.class);
        doReturn(bundle).when(bundleContext).getBundle();
        org.osgi.service.cm.Configuration config = mock(org.osgi.service.cm.Configuration.class);
        org.osgi.service.cm.Configuration persistedTokenStoreConfig = mock(org.osgi.service.cm.Configuration.class);
        doReturn(config).when(configurationAdmin).getConfiguration("com.qubular.openhab.binding.vicare.SimpleConfiguration");
        doReturn(persistedTokenStoreConfig).when(configurationAdmin).getConfiguration(PersistedTokenStore.TOKEN_STORE_PID);
        Dictionary<String, Object> ptsProps = new Hashtable<>();
        doReturn(ptsProps).when(persistedTokenStoreConfig).getProperties();
//        doAnswer(i -> ChannelTypeBuilder.state(i.getArgument(0), "Label", "Number:Temperature").build()).when(channelTypeRegistry).getChannelType(any(ChannelTypeUID.class));
        when(vicareServiceProvider.getVicareConfiguration()).thenReturn(configuration);
        when(vicareServiceProvider.getVicareService()).thenReturn(vicareService);
        when(vicareServiceProvider.getBindingVersion()).thenReturn("3.3.0");
        when(vicareServiceProvider.getThingRegistry()).thenReturn(thingRegistry);
        when(vicareServiceProvider.getBundleContext()).thenReturn(bundleContext);
        when(vicareServiceProvider.getConfigurationAdmin()).thenReturn(configurationAdmin);
        when(vicareServiceProvider.getChannelTypeRegistry()).thenReturn(myChannelTypeRegistry);
        doReturn(channelTypes.values()).when(xmlChannelTypeProvider).getChannelTypes(any(Locale.class));
        doAnswer(i -> channelTypes.get(i.getArgument(0))).when(xmlChannelTypeProvider).getChannelType(any(ChannelTypeUID.class), nullable(Locale.class));
        myChannelTypeRegistry.addChannelTypeProvider(xmlChannelTypeProvider);
    }

    @AfterEach
    void tearDown() {
        if (bridgeHandler != null) {
            bridgeHandler.dispose();
            bridgeHandler = null;
        }
    }

    @Test
    public void bridgeHandlerReportsDiscoveryService() throws AuthenticationException, IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();

        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        activateHandlerFactory(vicareHandlerFactory);

        BridgeHandler handler = (BridgeHandler) vicareHandlerFactory.createHandler(bridge);
        assertNotNull(handler);
        assertTrue(handler.getServices().contains(VicareDiscoveryService.class));
    }

    @Test
    public void canStartAScan() throws AuthenticationException, IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException, InterruptedException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();

        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        activateHandlerFactory(vicareHandlerFactory);

        bridgeHandler = vicareHandlerFactory.createHandler(bridge);

        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        InOrder inOrder1 = inOrder(vicareService, discoveryListener);
        VicareDiscoveryService discoveryService = VicareDiscoveryService.class.getConstructor().newInstance();
        discoveryService.setThingHandler(bridgeHandler);
        discoveryService.addDiscoveryListener(discoveryListener);

        inOrder1.verify(vicareService, timeout(1000).times(1)).getInstallations();
        ArgumentCaptor<DiscoveryResult> resultArgumentCaptor = forClass(DiscoveryResult.class);
        inOrder1.verify(discoveryListener, timeout(1000).times(1)).thingDiscovered(same(discoveryService), any(DiscoveryResult.class));

        InOrder inOrder2 = inOrder(vicareService, discoveryListener);
        Thread.sleep(1000);

        discoveryService.startScan();
        inOrder2.verify(vicareService, timeout(1000).times(1)).getInstallations();
        inOrder2.verify(discoveryListener, calls(1)).thingDiscovered(same(discoveryService), resultArgumentCaptor.capture());
        DiscoveryResult result = resultArgumentCaptor.getValue();
        assertEquals("vicare:heating:" + THING_UID_BRIDGE.getId() + ":0328bf05-9b58-35fe-9845-edfc5a9b09aa", result.getThingUID().getAsString());
        assertEquals(THING_UID_BRIDGE, result.getBridgeUID());
        assertEquals(encodeThingUniqueId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID), result.getProperties().get("deviceUniqueId"));
        assertEquals("deviceUniqueId", result.getRepresentationProperty());
        assertEquals("1234567890123245", result.getProperties().get(PROPERTY_BOILER_SERIAL));
        assertEquals(GATEWAY_SERIAL, result.getProperties().get(PROPERTY_GATEWAY_SERIAL));
        assertEquals("E3_Vitodens_100_0421", result.getProperties().get(PROPERTY_MODEL_ID));
        assertEquals(DEVICE_TYPE_HEATING, result.getProperties().get(PROPERTY_DEVICE_TYPE));
    }

    @Test
    public void discoveryServiceNotCancelledOnUnhandledException() throws AuthenticationException, IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException, InterruptedException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();

        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        activateHandlerFactory(vicareHandlerFactory);

        bridgeHandler = vicareHandlerFactory.createHandler(bridge);
        doThrow(new RuntimeException("Test exception")).when(vicareService).getInstallations();

        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        InOrder inOrder1 = inOrder(vicareService, discoveryListener);
        VicareDiscoveryService discoveryService = VicareDiscoveryService.class.getConstructor().newInstance();
        discoveryService.setThingHandler(bridgeHandler);
        discoveryService.addDiscoveryListener(discoveryListener);

        inOrder1.verify(vicareService, timeout(1000).times(1)).getInstallations();

        Thread.sleep(1000);

        assertTrue(discoveryService.isBackgroundDiscoveryRunning());
    }

    @Test
    public void startsBackgroundScan() throws AuthenticationException, IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();

        ThingHandlerCallback thingHandlerCallback = mock(ThingHandlerCallback.class);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        bridgeHandler = vicareHandlerFactory.createHandler(bridge);
        bridgeHandler.setCallback(thingHandlerCallback);
        VicareDiscoveryService discoveryService = VicareDiscoveryService.class.getConstructor().newInstance();
        discoveryService.setThingHandler(bridgeHandler);
        DiscoveryListener discoveryListener = mock(DiscoveryListener.class);
        discoveryService.addDiscoveryListener(discoveryListener);
        activateHandlerFactory(vicareHandlerFactory);
        activateDiscoveryService(discoveryService);

        assertTrue(discoveryService.isBackgroundDiscoveryEnabled());
        verify(vicareService, timeout(1000)).getInstallations();
        ArgumentCaptor<DiscoveryResult> resultArgumentCaptor = forClass(DiscoveryResult.class);
        verify(discoveryListener, timeout(1000)).thingDiscovered(same(discoveryService), resultArgumentCaptor.capture());
        DiscoveryResult result = resultArgumentCaptor.getValue();
        assertEquals("vicare:heating:" + THING_UID_BRIDGE.getId() + ":0328bf05-9b58-35fe-9845-edfc5a9b09aa", result.getThingUID().getAsString());
        assertEquals(THING_UID_BRIDGE, result.getBridgeUID());
        assertEquals(encodeThingUniqueId(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID), result.getProperties().get("deviceUniqueId"));
        assertEquals("deviceUniqueId", result.getRepresentationProperty());
        ArgumentCaptor<ThingStatusInfo> statusInfoArgumentCaptor = forClass(ThingStatusInfo.class);
        verify(thingHandlerCallback, timeout(1000)).statusUpdated(same(bridge), statusInfoArgumentCaptor.capture());
        assertEquals(ThingStatus.ONLINE, statusInfoArgumentCaptor.getValue().getStatus());
    }

    @Test
    public void handlerFactoryCreateDeviceThingHandler() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        vicareBridge();

        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);

        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);

        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);

        assertNotNull(handler);
    }

    @Test
    public void initializeDeviceHandlerNotCancelledOnUnhandledException() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        simpleHandlerCallback(bridge, handler);
        doThrow(new RuntimeException("Unexpected exception")).when(vicareService).getFeatures(anyLong(), anyString(), anyString());

        registerAndInitialize(bridgeHandler);
        registerAndInitialize(handler);
        verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);

        assertTrue(((VicareBridgeHandler)bridgeHandler).isFeatureScanRunning());
    }

    @Test
    public void initializeDeviceHandlerCreatesTemperatureSensor() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel channel = findChannel(thingCaptor, "heating_dhw_sensors_temperature_outlet");
        assertEquals("heating_dhw_sensors_temperature_outlet", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.outlet", channel.getProperties().get(PROPERTY_FEATURE_NAME));
        assertEquals("Number:Temperature", channel.getAcceptedItemType());

        Channel dhwHotWaterStorageTempChannel = findChannel(thingCaptor, "heating_dhw_sensors_temperature_hotWaterStorage");
        assertEquals("heating_dhw_sensors_temperature_hotWaterStorage", dhwHotWaterStorageTempChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.hotWaterStorage", dhwHotWaterStorageTempChannel.getProperties().get(PROPERTY_FEATURE_NAME));
        assertEquals("Number:Temperature", dhwHotWaterStorageTempChannel.getAcceptedItemType());

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(27.3, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);

        handler.handleCommand(dhwHotWaterStorageTempChannel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(dhwHotWaterStorageTempChannel.getUID()), stateCaptor.capture());
        assertEquals(54.3, ((DecimalType) stateCaptor.getValue()).doubleValue(), 0.01);

        channel = findChannel(thingCaptor, "heating_dhw_sensors_temperature_outlet_status");
        assertEquals("heating_dhw_sensors_temperature_outlet_status", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.outlet", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new StringType("connected"), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_dhw_sensors_temperature_hotWaterStorage_status");
        assertEquals("heating_dhw_sensors_temperature_hotWaterStorage_status", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.hotWaterStorage", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new StringType("connected"), stateCaptor.getValue());
    }

    static Stream<Arguments> source_heatingCircuitsOperatingPrograms() {
        return Stream.of(
                Arguments.of("normal", 21, OnOffType.ON, "Heating Circuit 0 Normal Operating Program Temperature", "Shows the Normal operating program target temperature for heating circuit 0", "Heating Circuit 0 Normal Operating Program Active", "Shows whether the Normal operating program is active for heating circuit 0", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normal/commands/setTemperature") ),
                Arguments.of("reduced", 12, OnOffType.OFF, "Heating Circuit 0 Reduced Operating Program Temperature", "Shows the Reduced operating program target temperature for heating circuit 0", "Heating Circuit 0 Reduced Operating Program Active", "Shows whether the Reduced operating program is active for heating circuit 0", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.reduced/commands/setTemperature")),
                Arguments.of("comfort", 22, OnOffType.OFF, "Heating Circuit 0 Comfort Operating Program Temperature", "Shows the Comfort operating program target temperature for heating circuit 0", "Heating Circuit 0 Comfort Operating Program Active", "Shows whether the Comfort operating program is active for heating circuit 0", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfort/commands/setTemperature")),
                Arguments.of("reducedHeating", 18, OnOffType.OFF, "Heating Circuit 0 Reduced Heating Operating Program Temperature", "Shows the Reduced Heating operating program target temperature for heating circuit 0", "Heating Circuit 0 Reduced Heating Operating Program Active", "Shows whether the Reduced Heating operating program is active for heating circuit 0", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.reducedHeating/commands/setTemperature")),
                Arguments.of("normalHeating", 21, OnOffType.ON, "Heating Circuit 0 Normal Heating Operating Program Temperature", "Shows the Normal Heating operating program target temperature for heating circuit 0", "Heating Circuit 0 Normal Heating Operating Program Active", "Shows whether the Normal Heating operating program is active for heating circuit 0", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normalHeating/commands/setTemperature")),
                Arguments.of("comfortHeating", 22, OnOffType.OFF, "Heating Circuit 0 Comfort Heating Operating Program Temperature", "Shows the Comfort Heating operating program target temperature for heating circuit 0", "Heating Circuit 0 Comfort Heating Operating Program Active", "Shows whether the Comfort Heating operating program is active for heating circuit 0", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfortHeating/commands/setTemperature")),
                Arguments.of("eco", 21, OnOffType.OFF, "Heating Circuit 0 Eco Operating Program Temperature", "Shows the Eco operating program target temperature for heating circuit 0", "Heating Circuit 0 Eco Operating Program Active", "Shows whether the Eco operating program is active for heating circuit 0", null, null, null),
                Arguments.of("external", 0, OnOffType.OFF, "Heating Circuit 0 External Operating Program Temperature", "Shows the External operating program target temperature for heating circuit 0", "Heating Circuit 0 External Operating Program Active", "Shows whether the External operating program is active for heating circuit 0", null, null, null)
                         );
    }

    @MethodSource("source_heatingCircuitsOperatingPrograms")
    @ParameterizedTest
    public void supportsHeatingCircuitOperatingPrograms(String featureSuffix,
                                                        int expectedTemperature,
                                                        OnOffType expectedActive,
                                                        String expectedTemperatureLabel,
                                                        String expectedTemperatureDescription,
                                                        String expectedActiveLabel,
                                                        String expectedActiveDescription,
                                                        Double expectedMin,
                                                        Double expectedMax,
                                                        URI expectedURI) throws AuthenticationException, IOException, CommandFailureException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());
        Channel channel = findChannelNoVerify(thingCaptor, "heating_circuits_0_operating_programs_" + featureSuffix);
        assertEquals("heating.circuits.0.operating.programs." + featureSuffix, channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType opProgramChannelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedTemperatureLabel, opProgramChannelType.getLabel());
        assertEquals(expectedTemperatureDescription, opProgramChannelType.getDescription());
        assertEquals("Heating", opProgramChannelType.getCategory());
        assertEquals("Number:Temperature", opProgramChannelType.getItemType());

        Channel activeChannel = findChannelNoVerify(thingCaptor, "heating_circuits_0_operating_programs_" + featureSuffix + "_active");
        assertEquals("heating.circuits.0.operating.programs." + featureSuffix, activeChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType opProgramActiveChannelType = channelTypeRegistry.getChannelType(activeChannel.getChannelTypeUID());
        assertEquals(expectedActiveLabel, opProgramActiveChannelType.getLabel());
        assertEquals(expectedActiveDescription, opProgramActiveChannelType.getDescription());
        assertEquals("Switch", opProgramActiveChannelType.getItemType());

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedTemperature, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);

        handler.handleCommand(activeChannel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(activeChannel.getUID()), stateCaptor.capture());
        assertEquals(expectedActive, stateCaptor.getValue());

        if (expectedURI != null) {
            assertEquals(BigDecimal.valueOf(expectedMin), opProgramChannelType.getState().getMinimum());
            assertEquals(BigDecimal.valueOf(expectedMax), opProgramChannelType.getState().getMaximum());
            assertEquals(BigDecimal.valueOf(1.0), opProgramChannelType.getState().getStep());
            assertFalse(opProgramChannelType.getState().isReadOnly());
            handler.handleCommand(channel.getUID(), QuantityType.valueOf("15  °C"));
            verify(vicareService, timeout(1000)).sendCommand(expectedURI, Map.of("targetTemperature", 15.0));
        }
    }

    @Test
    public void initializeDeviceHandlerCreatesConsumptionChannels() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);

        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());
        Map<String, Channel> channelsById = thingCaptor.getAllValues().stream()
                .flatMap(t -> t.getChannels().stream())
                .filter(c -> c.getProperties().get(PROPERTY_FEATURE_NAME).equals("heating.gas.consumption.summary.dhw"))
                .collect(Collectors.toMap(c -> c.getChannelTypeUID().getId(), identity()));
        assertTrue(channelsById.containsKey("heating_gas_consumption_summary_dhw_currentDay"));
        assertTrue(channelsById.containsKey("heating_gas_consumption_summary_dhw_lastSevenDays"));
        assertTrue(channelsById.containsKey("heating_gas_consumption_summary_dhw_currentMonth"));
        assertTrue(channelsById.containsKey("heating_gas_consumption_summary_dhw_currentYear"));
        Map<String, Double> expectedValues = Map.of(
                "heating_gas_consumption_summary_dhw_currentDay", 0.2,
                "heating_gas_consumption_summary_dhw_lastSevenDays", 2.1,
                "heating_gas_consumption_summary_dhw_currentMonth", 1.8,
                "heating_gas_consumption_summary_dhw_currentYear", 5.9
        );
        Map<String, String> statisticName = Map.of(
                "heating_gas_consumption_summary_dhw_currentDay", "currentDay",
                "heating_gas_consumption_summary_dhw_lastSevenDays", "lastSevenDays",
                "heating_gas_consumption_summary_dhw_currentMonth", "currentMonth",
                "heating_gas_consumption_summary_dhw_currentYear", "currentYear"
        );
        channelsById.values().forEach(c -> {
                    verifyChannel(c);
                    assertEquals(statisticName.get(c.getChannelTypeUID().getId()), c.getProperties().get(PROPERTY_PROP_NAME));
                    handler.handleCommand(c.getUID(), RefreshType.REFRESH);
                    ArgumentCaptor<State> stateCaptor = forClass(State.class);
                    verify(callback, timeout(1000)).stateUpdated(eq(c.getUID()), stateCaptor.capture());
                    assertEquals(expectedValues.get(c.getChannelTypeUID().getId()), ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);
        });
    }


    @Test
    public void initializeDeviceHandlerCreatesStatisticsSensor() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);

        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());
        Channel channel = findChannel(thingCaptor, "heating_burners_0_statistics_starts");
        assertEquals("heating_burners_statistics_starts", channel.getChannelTypeUID().getId());
        assertEquals("heating.burners.0.statistics", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(5.0, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);
    }

    @Test
    public void initializeDeviceHandlerCreatesTextChannel() throws AuthenticationException, IOException, InterruptedException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel channel = findChannel(thingCaptor, "device_serial_value");
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), eq(StringType.valueOf("7723181102527121")));
    }

    @Test
    public void supportsWritableEnumProperty() throws AuthenticationException, IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, CommandFailureException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel enumChannel = findChannel(thingCaptor, "heating_circuits_0_operating_modes_active_value");
        assertEquals("heating_circuits_operating_modes_active_value", enumChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.operating.modes.active", enumChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Optional<Class<? extends ThingHandlerService>> descriptionProviderClass = handler.getServices().stream().filter(
                DynamicCommandDescriptionProvider.class::isAssignableFrom).findAny();
        assertTrue(descriptionProviderClass.isPresent());
        ThingHandlerService thingHandlerService = descriptionProviderClass.get().getConstructor().newInstance();
        thingHandlerService.setThingHandler(handler);
        thingHandlerService.activate();
        DynamicCommandDescriptionProvider descriptionProvider = (DynamicCommandDescriptionProvider) thingHandlerService;
        CommandDescription commandDescription = descriptionProvider.getCommandDescription(enumChannel,
                                                                                          CommandDescriptionBuilder.create().build(),
                                                                                          Locale.getDefault());


        assertEquals(4, commandDescription.getCommandOptions().size());
        Set<String> options = commandDescription.getCommandOptions().stream().map(
                CommandOption::getCommand).collect(
                Collectors.toSet());
        assertEquals(Set.of("standby", "heating", "dhw", "dhwAndHeating"), options);


        handler.handleCommand(enumChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(enumChannel.getUID()), stateCaptor.capture());

        assertEquals(new StringType("dhw"), stateCaptor.getValue());

        handler.handleCommand(enumChannel.getUID(), new StringType("heating"));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        inOrder.verify(vicareService, timeout(1000)).sendCommand(eq(SET_MODE_URI), eq(Map.of("mode", "heating")));
    }

    private ThingHandlerCallback simpleHandlerCallback(Bridge bridge, ThingHandler handler) {
        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        when(callback.getBridge(THING_UID_BRIDGE)).thenReturn(bridge);
        handler.setCallback(callback);

        doAnswer(invocation -> {
            Thing thing = invocation.getArgument(0);
            ThingUID uid = thing.getUID();
            doReturn(thing).when(thingRegistry).get(uid);
            return null;
        }).when(callback).thingUpdated(any(Thing.class));
        doAnswer(invocation -> ChannelBuilder.create(invocation.getArgument(0, ChannelUID.class))
                .withType(invocation.getArgument(1, ChannelTypeUID.class))
                .withAcceptedItemType("Number:Temperature")).when(callback).createChannelBuilder(any(ChannelUID.class), any(ChannelTypeUID.class));
        return callback;
    }

    @Test
    public void initializeDeviceHandlerCreatesCurve() throws AuthenticationException, IOException, InterruptedException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel slopeChannel = findChannel(thingCaptor, "heating_circuits_0_heating_curve_slope");
        assertNotNull(slopeChannel);
        assertEquals("heating_circuits_heating_curve_slope", slopeChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.heating.curve", slopeChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel shiftChannel = findChannel(thingCaptor, "heating_circuits_0_heating_curve_shift");
        assertNotNull(shiftChannel);
        assertEquals("heating_circuits_heating_curve_shift", shiftChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.heating.curve", shiftChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(slopeChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(slopeChannel.getUID()), stateCaptor.capture());
        assertEquals(1.6, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);

        handler.handleCommand(shiftChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        verify(callback, timeout(1000)).stateUpdated(eq(shiftChannel.getUID()), stateCaptor.capture());
        assertEquals(-4.0, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);
    }

    @Test
    public void initializeDeviceHandlerCreatesStatusSensor() throws AuthenticationException, IOException, InterruptedException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel channel = findChannel(thingCaptor, "heating_circuits_0_circulation_pump_status");
        assertNotNull(channel);
        assertEquals("heating_circuits_circulation_pump_status", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.circulation.pump", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel burnerChannel = findChannel(thingCaptor, "heating_burners_0_active");
        assertNotNull(burnerChannel);

        assertEquals("heating_burners_active", burnerChannel.getChannelTypeUID().getId());
        assertEquals("heating.burners.0", burnerChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel heatingDhwActiveChannel = findChannel(thingCaptor, "heating_dhw_active");
        assertEquals("heating_dhw_active", heatingDhwActiveChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw", heatingDhwActiveChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel heatingDhwStatusChannel = findChannel(thingCaptor, "heating_dhw_status");
        assertEquals("heating_dhw_status", heatingDhwStatusChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw", heatingDhwStatusChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("on"), stateCaptor.getValue());

        handler.handleCommand(burnerChannel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(burnerChannel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());

        handler.handleCommand(heatingDhwActiveChannel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(heatingDhwActiveChannel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());

        handler.handleCommand(heatingDhwStatusChannel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(heatingDhwStatusChannel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("on"), stateCaptor.getValue());
        inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
    }

    private void createBridgeHandler(Bridge bridge) {
        VicareBridgeHandler bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        bridgeHandler.setCallback(mock(ThingHandlerCallback.class));
        when(bridge.getHandler()).thenReturn(bridgeHandler);
    }

    @Test
    public void supportsHeatingCircuit() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        Channel channel = findChannel(thingCaptor, "heating_circuits_0_name");
        assertNotNull(channel);
        assertEquals("heating_circuits_name", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("circuitName"), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCircuitName() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        Channel channel = findChannel(thingCaptor, "heating_circuits_0_name_name");
        assertNotNull(channel);
        assertEquals("heating_circuits_name_name", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.name", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("circuitNameName"), stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingCircuitsOperatingModes() {
        return Stream.of(
                Arguments.of("dhw", OnOffType.OFF, "Heating Circuit DHW Operating Mode Active", "Shows whether the domestic hot water (DHW) only operating mode is active."),
                Arguments.of("dhwAndHeating", OnOffType.ON, "Heating Circuit DHW And Heating Operating Mode Active", "Shows whether the domestic hot water (DHW) and heating operating mode is active."),
                Arguments.of("standby", OnOffType.OFF, "Heating Circuit Standby Operating Mode Active", "Shows whether the Standby operating mode is active now. In this mode, the device will only start heating to protect installation from frost. Other commands, e.g. charging of DHW (oneTimeCharge), are still executable while this operating mode is active."),
                Arguments.of("heating", OnOffType.OFF, "Heating Circuit heating Operating Mode Active", "Shows whether the heating operating mode is active.")
        );
    }

    @ParameterizedTest()
    @MethodSource("source_heatingCircuitsOperatingModes")
    public void supportsHeatingCircuitOperatingMode(String suffix,
                                                    OnOffType expectedActive,
                                                    String expectedLabel,
                                                    String expectedDescription) throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        Channel channel = findChannelNoVerify(thingCaptor, String.format("heating_circuits_1_operating_modes_%s_active", suffix));
        assertNotNull(channel);
        assertEquals(String.format("heating.circuits.1.operating.modes.%s", suffix), channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedActive, stateCaptor.getValue());
    }

    private void registerAndInitialize(ThingHandler handler) {
        handler.getServices().forEach(
                c -> {
                    try {
                        if (ChannelTypeProvider.class.isAssignableFrom(c)) {
                            ThingHandlerService thingHandlerService = c.getConstructor().newInstance();
                            thingHandlerService.setThingHandler(handler);
                            thingHandlerService.activate();
                            channelTypeRegistry.addChannelTypeProvider((ChannelTypeProvider) thingHandlerService);
                        }
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                             NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
        handler.initialize();
    }

    @Test
    public void supportsHeatingCircuitOperatingProgramsForcedLastFromSchedule() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        Channel channel = findChannelNoVerify(thingCaptor, "heating_circuits_1_operating_programs_forcedLastFromSchedule_active");
        assertNotNull(channel);
        assertEquals("heating.circuits.1.operating.programs.forcedLastFromSchedule", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Heating Circuit 1 Extended Heating Active", channelType.getLabel());
        assertEquals("If activated, the last program activated by the schedule will be sustained until this feature is deactivated.", channelType.getDescription());

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingCircuitsOperatingProgramsStatusSensorFeatures() {
        return Stream.of(
                Arguments.of("standby", 1, OnOffType.OFF, "Heating Circuit 1 Standby Operating Program Active", "Shows whether the Standby operating program is active for heating circuit 1", null, null, null, null),
                Arguments.of("summerEco", 1, OnOffType.OFF, "Heating Circuit 1 Summer Eco Operating Program Active", "Shows whether the Summer Eco operating program is active for heating circuit 1", null, null, null, null),
                Arguments.of("fixed", 1, OnOffType.OFF, "Heating Circuit 1 Fixed Operating Program Active", "Shows whether the Fixed operating program is active for heating circuit 1", null, null, null, null),
                Arguments.of("normalEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Normal Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Normal operating program is scheduled on heating circuit 1",
                             "Heating Circuit 1 Normal Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Normal Energy Saving Operating Program Demand", "heating"),
                Arguments.of("reducedEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Reduced Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Reduced operating program is scheduled on heating circuit 1",
                             "Heating Circuit 1 Reduced Energy Saving Operating Program Reason", "unknown",
                             "Heating Circuit 1 Reduced Energy Saving Operating Program Demand", "heating"),
                Arguments.of("comfortEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Comfort Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Comfort operating program is scheduled on heating circuit 1",
                             "Heating Circuit 1 Comfort Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Comfort Energy Saving Operating Program Demand", "heating"),
                Arguments.of("normalCoolingEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Normal Cooling Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Normal Cooling operating program is scheduled on heating circuit 1",
                             "Heating Circuit 1 Normal Cooling Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Normal Cooling Energy Saving Operating Program Demand", "cooling"),
                Arguments.of("reducedCoolingEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Reduced Cooling Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Reduced Cooling operating program is scheduled on heating circuit 1",
                             "Heating Circuit 1 Reduced Cooling Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Reduced Cooling Energy Saving Operating Program Demand", "cooling"),
                Arguments.of("comfortCoolingEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Comfort Cooling Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Comfort Cooling operating program is scheduled on heating circuit 1",
                             "Heating Circuit 1 Comfort Cooling Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Comfort Cooling Energy Saving Operating Program Demand", "cooling")
                         );
    }

    @MethodSource("source_heatingCircuitsOperatingProgramsStatusSensorFeatures")
    @ParameterizedTest
    public void supportsHeatingCircuitOperatingProgramsStatusSensorFeatures(String suffix,
                                                                            int heatingCircuit,
                                                                            OnOffType expectedActive,
                                                                            String expectedLabel,
                                                                            String expectedDescription,
                                                                            String expectedReasonLabel,
                                                                            String expectedReason,
                                                                            String expectedDemandLabel,
                                                                            String expectedDemand) throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        Channel channel = findChannelNoVerify(thingCaptor, "heating_circuits_" + heatingCircuit + "_operating_programs_" + suffix + "_active");
        assertNotNull(channel);
        assertEquals("heating.circuits." + heatingCircuit + ".operating.programs." + suffix, channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());
        assertTrue(channelType.getState().isReadOnly());
        assertEquals("Switch", channelType.getItemType());

        if (expectedReason != null) {
            Channel reasonChannel = findChannelNoVerify(thingCaptor, String.format("heating_circuits_%s_operating_programs_%s_reason", heatingCircuit, suffix));
            ChannelType reasonChannelType = channelTypeRegistry.getChannelType(reasonChannel.getChannelTypeUID());
            assertEquals(expectedReasonLabel, reasonChannelType.getLabel());
            assertEquals("String", reasonChannelType.getItemType());

            handler.handleCommand(reasonChannel.getUID(), RefreshType.REFRESH);
            ArgumentCaptor<State> reasonCaptor = ArgumentCaptor.forClass(State.class);
            verify(callback, timeout(1000)).stateUpdated(eq(reasonChannel.getUID()), reasonCaptor.capture());
            assertEquals(StringType.valueOf(expectedReason), reasonCaptor.getValue());
            assertTrue(reasonChannelType.getState().isReadOnly());
        }

        if (expectedDemand != null) {
            Channel demandChannel = findChannelNoVerify(thingCaptor, String.format("heating_circuits_%s_operating_programs_%s_demand", heatingCircuit, suffix));
            ChannelType demandChannelType = channelTypeRegistry.getChannelType(demandChannel.getChannelTypeUID());
            assertEquals(expectedDemandLabel, demandChannelType.getLabel());
            assertEquals("String", demandChannelType.getItemType());

            handler.handleCommand(demandChannel.getUID(), RefreshType.REFRESH);
            ArgumentCaptor<State> demandCaptor = ArgumentCaptor.forClass(State.class);
            verify(callback, timeout(1000)).stateUpdated(eq(demandChannel.getUID()), demandCaptor.capture());
            assertEquals(StringType.valueOf(expectedDemand), demandCaptor.getValue());
            assertTrue(demandChannelType.getState().isReadOnly());
        }

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedActive, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCircuitSensorsTemperatureSupply() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        Channel channel = findChannel(thingCaptor, "heating_circuits_1_sensors_temperature_supply");
        assertNotNull(channel);
        assertEquals("heating_circuits_sensors_temperature_supply", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.1.sensors.temperature.supply", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(DecimalType.valueOf("24.6"), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_circuits_1_sensors_temperature_supply_status");
        assertNotNull(channel);
        assertEquals("heating_circuits_sensors_temperature_supply_status", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.1.sensors.temperature.supply", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("connected"), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCircuitZoneMode() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        Channel channel = findChannel(thingCaptor, "heating_circuits_1_zone_mode_active");
        assertNotNull(channel);
        assertEquals("heating_circuits_zone_mode_active", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.1.zone.mode", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCircuitTemperatureLevels() throws AuthenticationException, IOException, CommandFailureException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel minChannel = findChannel(thingCaptor, "heating_circuits_0_temperature_levels_min");
        assertNotNull(minChannel);
        assertEquals("heating_circuits_temperature_levels_min", minChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.temperature.levels", minChannel.getProperties().get(PROPERTY_FEATURE_NAME));
        Channel maxChannel = findChannel(thingCaptor, "heating_circuits_0_temperature_levels_max");
        assertNotNull(maxChannel);
        assertEquals("heating_circuits_temperature_levels_max", maxChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.temperature.levels", maxChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(minChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(minChannel.getUID()), stateCaptor.capture());
        assertEquals(DecimalType.valueOf("20"), stateCaptor.getValue());
        handler.handleCommand(maxChannel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(maxChannel.getUID()), stateCaptor.capture());
        assertEquals(DecimalType.valueOf("45"), stateCaptor.getValue());

        handler.handleCommand(maxChannel.getUID(), QuantityType.valueOf("46  °C"));
        inOrder.verify(vicareService, timeout(1000)).sendCommand(SET_LEVEL_MAX_URI, Map.of("temperature", 46.0));
    }

    @Test
    public void supportsHeatingDhwHygiene() throws AuthenticationException, IOException, CommandFailureException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel channel = findChannel(thingCaptor, "heating_dhw_hygiene_enabled");
        assertNotNull(channel);
        assertEquals("heating_dhw_hygiene_enabled", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.hygiene", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingDhwOneTimeCharge() throws AuthenticationException, IOException, CommandFailureException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel channel = findChannel(thingCaptor, "heating_dhw_oneTimeCharge_active");
        assertNotNull(channel);
        assertEquals("heating_dhw_oneTimeCharge_active", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.oneTimeCharge", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_dhw_oneTimeCharge_activate");
        assertNotNull(channel);
        assertEquals("heating_dhw_oneTimeCharge_activate", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.oneTimeCharge", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), OnOffType.ON);
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/activate"), emptyMap());

        channel = findChannel(thingCaptor, "heating_dhw_oneTimeCharge_deactivate");
        assertNotNull(channel);
        assertEquals("heating_dhw_oneTimeCharge_deactivate", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.oneTimeCharge", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), OnOffType.ON);
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/deactivate"), emptyMap());
    }

    @Test
    public void supportsHeatingDhwOperatingModesOff() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel channel = findChannel(thingCaptor, "heating_dhw_operating_modes_off_active");
        assertNotNull(channel);
        assertEquals("heating_dhw_operating_modes_off_active", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.operating.modes.off", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingDhwTemperatureMain() throws AuthenticationException, IOException, CommandFailureException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel tempChannel = findChannel(thingCaptor, "heating_dhw_temperature_main");
        assertNotNull(tempChannel);
        assertEquals("heating_dhw_temperature_main", tempChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.temperature.main", tempChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(tempChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(tempChannel.getUID()), stateCaptor.capture());
        assertEquals(DecimalType.valueOf("50.0"), stateCaptor.getValue());

        handler.handleCommand(tempChannel.getUID(), QuantityType.valueOf("51  °C"));
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.dhw.temperature.main/commands/setTargetTemperature"),
                Map.of("temperature", 51.0));
    }

    private static Channel findChannel(ArgumentCaptor<Thing> thingCaptor, String channelId) {
        Channel channel = findChannelNoVerify(thingCaptor, channelId);
        verifyChannel(channel);
        return channel;
    }

    private static Channel findChannelNoVerify(ArgumentCaptor<Thing> thingCaptor, String channelId) {
        List<Channel> matched = thingCaptor.getAllValues().stream()
                .flatMap(t -> t.getChannels().stream())
                .filter(c -> c.getUID().getId().equals(channelId))
                .collect(Collectors.toList());
        assertTrue(matched.size() <= 1);
        if (matched.isEmpty()) return null;

        Channel channel = matched.get(0);
        return channel;
    }

    @Test
    public void initializeDeviceHandlerCreatesDatePeriod() throws AuthenticationException, IOException, InterruptedException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel channel = findChannel(thingCaptor, "heating_circuits_0_operating_programs_holiday_active");
        assertNotNull(channel);
        assertEquals("heating_circuits_operating_programs_holiday_active", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.operating.programs.holiday", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel startChannel = findChannel(thingCaptor, "heating_circuits_0_operating_programs_holiday_start");
        assertNotNull(startChannel);

        assertEquals("heating_circuits_operating_programs_holiday_start", startChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.operating.programs.holiday", startChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel endChannel = findChannel(thingCaptor, "heating_circuits_0_operating_programs_holiday_end");
        assertNotNull(startChannel);

        assertEquals("heating_circuits_operating_programs_holiday_end", endChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.operating.programs.holiday", endChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());

        handler.handleCommand(startChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        verify(callback, timeout(1000)).stateUpdated(eq(startChannel.getUID()), stateCaptor.capture());
        assertEquals(ZonedDateTime.parse("2022-12-23T00:00:00Z"), ((DateTimeType)stateCaptor.getValue()).getZonedDateTime());

        handler.handleCommand(endChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        verify(callback, timeout(1000)).stateUpdated(eq(endChannel.getUID()), stateCaptor.capture());
        assertEquals(ZonedDateTime.parse("2022-12-26T23:59:59.999999999Z"), ((DateTimeType)stateCaptor.getValue()).getZonedDateTime());
    }

    @Test
    public void initializeBridgeSetsStatusToUnknown() {
        Bridge bridge = vicareBridge();
        VicareBridgeHandler vicareBridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        vicareBridgeHandler.setCallback(callback);
        vicareBridgeHandler.initialize();

        ArgumentCaptor<ThingStatusInfo> thingStatusCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        verify(callback).statusUpdated(same(bridge), thingStatusCaptor.capture());
        assertEquals(ThingStatus.UNKNOWN, thingStatusCaptor.getValue().getStatus());

    }

    @Test
    public void bridgeCachesResponsesPerDevice() throws AuthenticationException, IOException {
        oneHeatingInstallationTwoBoilers();
        Bridge bridge = vicareBridge();
        Thing boiler1 = heatingDeviceThing(DEVICE_1_ID);
        Thing boiler2 = heatingDeviceThing(DEVICE_2_ID);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        when(bridge.getHandler()).thenReturn((BridgeHandler) bridgeHandler);
        ThingHandler boilerHandler1 = vicareHandlerFactory.createHandler(boiler1);
        ThingHandler boilerHandler2 = vicareHandlerFactory.createHandler(boiler2);
        ThingHandlerCallback thingHandlerCallback1 = simpleHandlerCallback(bridge, boilerHandler1);
        boilerHandler1.setCallback(thingHandlerCallback1);
        ThingHandlerCallback thingHandlerCallback2 = simpleHandlerCallback(bridge, boilerHandler2);
        boilerHandler2.setCallback(thingHandlerCallback2);

        registerAndInitialize(boilerHandler1);
        verify(thingHandlerCallback1, timeout(1000)).statusUpdated(any(Thing.class), argThat(tsi -> tsi.getStatus() == ThingStatus.ONLINE));

        registerAndInitialize(boilerHandler2);
        verify(thingHandlerCallback2, timeout(1000)).statusUpdated(any(Thing.class), argThat(tsi -> tsi.getStatus() == ThingStatus.ONLINE));

        verify(thingHandlerCallback1, timeout(1000)).thingUpdated(any(Thing.class));
        ChannelUID channel1UID = new ChannelUID(boiler1.getUID(), "device_serial_value");
        boilerHandler1.handleCommand(channel1UID, RefreshType.REFRESH);
        ArgumentCaptor<State> stateCaptor = ArgumentCaptor.forClass(State.class);
        verify(thingHandlerCallback1, timeout(1000)).stateUpdated(eq(channel1UID), stateCaptor.capture());
        assertEquals(StringType.valueOf("1111111111111111"), stateCaptor.getValue());

        verify(thingHandlerCallback2, timeout(1000)).thingUpdated(any(Thing.class));
        ChannelUID channel2UID = new ChannelUID(boiler2.getUID(), "device_serial_value");
        boilerHandler2.handleCommand(channel2UID, RefreshType.REFRESH);
        ArgumentCaptor<State> stateCaptor2 = ArgumentCaptor.forClass(State.class);
        verify(thingHandlerCallback2, timeout(1000)).stateUpdated(eq(channel2UID), stateCaptor2.capture());
        assertEquals(StringType.valueOf("2222222222222222"), stateCaptor2.getValue());
    }

    @Test
    public void supportsHeatingSolarSensorsTemperatureCollector() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        Channel tempChannel = findChannel(thingCaptor, "heating_solar_sensors_temperature_collector");
        assertNotNull(tempChannel);
        assertEquals("heating_solar_sensors_temperature_collector", tempChannel.getChannelTypeUID().getId());
        assertEquals("heating.solar.sensors.temperature.collector", tempChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        handler.handleCommand(tempChannel.getUID(), RefreshType.REFRESH);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(callback, timeout(1000)).stateUpdated(eq(tempChannel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(35.4), stateCaptor.getValue());
    }

    @Test
    public void supportsSolarPowerProduction() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(thingCaptor, "heating_solar_power_production_currentDay");
        assertEquals("heating_solar_power_production_currentDay", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(0), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_solar_power_production_previousDay");
        assertEquals("heating_solar_power_production_previousDay", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(11.4), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_solar_power_production_currentWeek");
        assertEquals("heating_solar_power_production_currentWeek", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(31), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_solar_power_production_previousWeek");
        assertEquals("heating_solar_power_production_previousWeek", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(58.3), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_solar_power_production_currentMonth");
        assertEquals("heating_solar_power_production_currentMonth", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(247.8), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_solar_power_production_previousMonth");
        assertEquals("heating_solar_power_production_previousMonth", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(355.5), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_solar_power_production_currentYear");
        assertEquals("heating_solar_power_production_currentYear", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(4250.6), stateCaptor.getValue());

        channel = findChannel(thingCaptor, "heating_solar_power_production_previousYear");
        assertEquals("heating_solar_power_production_previousYear", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(0), stateCaptor.getValue());
    }

    @Test
    public void supportsSolarSensorsTemperatureDHW() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(thingCaptor, "heating_solar_sensors_temperature_dhw");
        assertEquals("heating_solar_sensors_temperature_dhw", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(28.1), stateCaptor.getValue());
    }

    @Test
    public void supportsSolarPumpsCircuit() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(thingCaptor, "heating_solar_pumps_circuit_status");
        assertEquals("heating_solar_pumps_circuit_status", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("off"), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingSolar() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        Bridge bridge = vicareBridge();
        createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        verify(callback, timeout(1000).atLeastOnce()).thingUpdated(thingCaptor.capture());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(thingCaptor, "heating_solar_active");
        assertEquals("heating_solar_active", channel.getChannelTypeUID().getId());
        handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());
    }

    private Thing heatingDeviceThing(String deviceId) {
        Thing deviceThing = mock(Thing.class);
        doReturn(THING_UID_BRIDGE).when(deviceThing).getBridgeUID();
        doReturn(THING_TYPE_HEATING).when(deviceThing).getThingTypeUID();
        doReturn(new Configuration()).when(deviceThing).getConfiguration();
        ThingUID deviceThingId = new ThingUID(THING_TYPE_HEATING, THING_UID_BRIDGE, encodeThingId(INSTALLATION_ID, GATEWAY_SERIAL, deviceId));
        doReturn(deviceThingId).when(deviceThing).getUID();
        Map<String, String> propMap = new HashMap<>(Map.of(PROPERTY_DEVICE_UNIQUE_ID, encodeThingUniqueId(INSTALLATION_ID, GATEWAY_SERIAL, deviceId)));
        doReturn(propMap).when(deviceThing).getProperties();
        doAnswer(i -> {
            propMap.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(deviceThing).setProperty(anyString(), nullable(String.class));
        doReturn(THING_UID_BRIDGE).when(deviceThing).getBridgeUID();
        doReturn(deviceThing).when(thingRegistry).get(deviceThingId);
        return deviceThing;
    }

    private void activateHandlerFactory(VicareHandlerFactory vicareHandlerFactory) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method activate = BaseThingHandlerFactory.class.getDeclaredMethod("activate", ComponentContext.class);
        activate.setAccessible(true);
        activate.invoke(vicareHandlerFactory, componentContext);
    }

    private void activateDiscoveryService(VicareDiscoveryService discoveryService) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method activate = AbstractDiscoveryService.class.getDeclaredMethod("activate", Map.class);
        activate.setAccessible(true);
        activate.invoke(discoveryService, emptyMap());
    }

    private static void verifyChannel(Channel channel) {
        ChannelType channelType = channelTypes.get(channel.getChannelTypeUID());
        assertNotNull(channelType, String.format("Channel type %s not in thing-types.xml", channel.getChannelTypeUID().getId()));
    }

    private static Map<ChannelTypeUID, ChannelType> readChannelTypes() {
        try {
            XMLStreamReader xmlStreamReader = XMLInputFactory.newFactory().createXMLStreamReader(
                    VicareBindingTest.class.getResourceAsStream("/OH-INF/thing/thing-types.xml"));
            ThingTypeXmlReader thingTypeXmlReader = new ThingTypeXmlReader();
            thingTypeXmlReader.readChannelTypes(xmlStreamReader);
            return thingTypeXmlReader.getChannelTypes();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static class MyChannelTypeRegistry extends ChannelTypeRegistry {
        @Override
        protected void addChannelTypeProvider(ChannelTypeProvider channelTypeProviders) {
            super.addChannelTypeProvider(channelTypeProviders);
        }

        @Override
        protected void removeChannelTypeProvider(ChannelTypeProvider channelTypeProviders) {
            super.removeChannelTypeProvider(channelTypeProviders);
        }
    }
}
