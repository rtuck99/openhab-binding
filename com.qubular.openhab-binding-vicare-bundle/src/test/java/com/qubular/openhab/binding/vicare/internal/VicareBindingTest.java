package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.configuration.SimpleConfiguration;
import com.qubular.openhab.binding.vicare.internal.tokenstore.PersistedTokenStore;
import com.qubular.vicare.*;
import com.qubular.vicare.model.*;
import com.qubular.vicare.model.features.*;
import com.qubular.vicare.model.params.EnumParamDescriptor;
import com.qubular.vicare.model.params.NumericParamDescriptor;
import com.qubular.vicare.model.values.*;
import org.eclipse.jdt.annotation.Nullable;
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
import org.mockito.stubbing.OngoingStubbing;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryListener;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.library.types.*;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.*;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.*;
import org.openhab.core.types.*;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
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
import static org.openhab.core.library.unit.SIUnits.CELSIUS;

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
    private MockChannelTypeRegistry channelTypeRegistry;
    private static XmlChannelTypeProvider xmlChannelTypeProvider;

    private ComponentContext componentContext;
    private BundleContext bundleContext;

    private ThingHandler bridgeHandler;

    private void disconnectedHeatingInstallation() throws AuthenticationException, IOException {
        VicareError error = mock(VicareError.class);
        when(error.getErrorType()).thenReturn(VicareError.ERROR_TYPE_DEVICE_COMMUNICATION_ERROR);
        when(error.getStatusCode()).thenReturn(400);
        VicareError.ExtendedPayload extendedPayload = mock(VicareError.ExtendedPayload.class);
        when(extendedPayload.getCode()).thenReturn(404);
        when(extendedPayload.getReason()).thenReturn(VicareError.ExtendedPayload.REASON_GATEWAY_OFFLINE);
        when(error.getExtendedPayload()).thenReturn(extendedPayload);

        simpleHeatingInstallation().thenThrow(new VicareServiceException(error));
    }

    private OngoingStubbing<List<Feature>> simpleHeatingInstallation() throws AuthenticationException, IOException {
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
                                                                List.of(new CommandDescriptor("setTemperature", true, params, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfort/commands/setTemperature")),
                                                                        new CommandDescriptor("activate", true, emptyList(), URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfort/commands/activate")),
                                                                        new CommandDescriptor("deactivate", false, emptyList(), URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfort/commands/deactivate"))),
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
        Feature operatingProgramsActive = new TextFeature("heating.circuits.1.operating.programs.active", "value", "normalHeating");
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
        Feature heatingDhwCharging = new StatusSensorFeature("heating.dhw.charging", StatusValue.NA, false);
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
        Feature heatingGasConsumptionDhw = new ConsumptionTotalFeature("heating.gas.consumption.dhw",
                                                                       Map.of("day", new ArrayValue(Unit.KILOWATT_HOUR, new double[]{0.7, 1.3, 1.7, 1, 1, 1.5, 0.8, 0.3}),
                                                                              "week", new ArrayValue(Unit.CUBIC_METRE, new double[]{0.7, 7.6, 6.5, 10.1, 8.6, 9.1}),
                                                                              "month", new ArrayValue(Unit.CUBIC_METRE, new double[]{11.3, 39.8, 33.4, 35.4, 25.6, 25.7, 37.8, 41, 44.3, 40.5, 47.2, 44.7, 51.6})));
        Feature solarPowerCumulativeProduced = new NumericSensorFeature("heating.solar.power.cumulativeProduced", "value", new DimensionalValue(Unit.KILOWATT_HOUR, 14091.0), StatusValue.NA, null);
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
        List<Feature> heatingCircuitsSensorsTemperatureSupply = List.of(
                new NumericSensorFeature("heating.circuits.1.sensors.temperature.supply",
                                         "value",
                                         new DimensionalValue(Unit.CELSIUS, 24.6),
                                         new StatusValue("connected"),
                                         false),
                new NumericSensorFeature("heating.primaryCircuit.sensors.temperature.supply",
                                         "value",
                                         new DimensionalValue(Unit.CELSIUS, 17.4),
                                         new StatusValue("connected"),
                                         null),
                new NumericSensorFeature("heating.secondaryCircuit.sensors.temperature.supply",
                                         "value",
                                         new DimensionalValue(Unit.CELSIUS, 34.1),
                                         new StatusValue("connected"),
                                         null),
                new NumericSensorFeature("heating.circuits.0.sensors.temperature.room",
                                         "value",
                                         new DimensionalValue(Unit.CELSIUS, 24.1),
                                         new StatusValue("connected"),
                                         null)
                );
        Feature heatingCircuitsZoneMode = new StatusSensorFeature("heating.circuits.1.zone.mode",
                                                                  StatusValue.NA,
                                                                  false);
        Feature heatingDHWHygiene = new StatusSensorFeature("heating.dhw.hygiene",
                                                            Map.of("enabled", BooleanValue.FALSE));
        Feature heatingDHWOneTimeCharge = new StatusSensorFeature("heating.dhw.oneTimeCharge",
                                                                  Map.of("active", BooleanValue.FALSE),
                                                                  List.of(new CommandDescriptor("activate", true, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/activate")),
                                                                          new CommandDescriptor("deactivate", false, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/deactivate"))));
        List<Feature> heatingDHWOperatingModes = List.of(new StatusSensorFeature("heating.dhw.operating.modes.off", StatusValue.NA, false),
            new StatusSensorFeature("heating.dhw.operating.modes.balanced", StatusValue.NA, false),
            new StatusSensorFeature("heating.dhw.operating.modes.comfort", StatusValue.NA, true),
            new StatusSensorFeature("heating.dhw.operating.modes.eco", StatusValue.NA, false),
            new TextFeature("heating.dhw.operating.modes.active", "value", "eco", List.of(new CommandDescriptor("setMode", true, List.of(new EnumParamDescriptor(true, "mode", Set.of("eco", "comfort", "off"))),
                                                                                    URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.dhw.operating.modes.active/commands/setMode"))))
                                                         );
        List<Feature> heatingSensors = List.of(
                new NumericSensorFeature("heating.sensors.temperature.outside", "value", new DimensionalValue(Unit.CELSIUS, 15.8), new StatusValue("connected"), null),
                new NumericSensorFeature("heating.sensors.temperature.return", "value", new DimensionalValue(Unit.CELSIUS, 34.1), new StatusValue("connected"), null),
                new NumericSensorFeature("heating.sensors.volumetricFlow.allengra", "value", new DimensionalValue(Unit.CELSIUS, 274), new StatusValue("connected"), null)
        );
        Feature heatingCompressors0 = new StatusSensorFeature("heating.compressors.0",
                                                             Map.of("phase", new StringValue("ready"),
                                                                    "active", BooleanValue.FALSE));
        Feature heatingCompressors0Statistics = new StatusSensorFeature("heating.compressors.0.statistics",
                                                                        Map.of("starts", new DimensionalValue(new Unit(""), 177),
                                                                               "hours", new DimensionalValue(new Unit("hour"), 29), 
                                                                               "hoursLoadClassOne", new DimensionalValue(Unit.HOUR, 253),
                                                                               "hoursLoadClassTwo", new DimensionalValue(Unit.HOUR, 519),
                                                                               "hoursLoadClassThree", new DimensionalValue(Unit.HOUR, 1962),
                                                                               "hoursLoadClassFour", new DimensionalValue(Unit.HOUR, 257),
                                                                               "hoursLoadClassFive", new DimensionalValue(Unit.HOUR, 71)));
        Feature heatingBufferTemperatureSensorTop = new StatusSensorFeature("heating.buffer.sensors.temperature.top", Map.of("status", StatusValue.NOT_CONNECTED));
        Feature heatingBufferTemperatureSensorMain = new StatusSensorFeature("heating.buffer.sensors.temperature.main", Map.of("status", StatusValue.CONNECTED,
                                                                                                                               "value", new DimensionalValue(Unit.CELSIUS, 28.9)));
        Feature heatingDhwHotwaterStorageSensorTop = new NumericSensorFeature("heating.dhw.sensors.temperature.hotWaterStorage.top", Map.of("status", new StatusValue("connected"),
                "value", new DimensionalValue(Unit.CELSIUS, 44.4)), emptyList(), "value");
        Feature heatingDhwHotwaterStorageSensorBottom = new StatusSensorFeature("heating.dhw.sensors.temperature.hotWaterStorage.bottom", Map.of("status", new StatusValue("notConnected")));
        Feature heatingDhwTemperatureHysteresis = new NumericSensorFeature("heating.dhw.temperature.hysteresis", Map.of("value", new DimensionalValue(Unit.KELVIN, 5),
                "switchOnValue", new DimensionalValue(Unit.KELVIN, 4),
                "switchOffValue", new DimensionalValue(Unit.KELVIN, 6)),
                List.of(
                        new CommandDescriptor("setHysteresis", true, List.of(new NumericParamDescriptor(true, "hysteresis", 1.0, 10.0, 0.5)), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.hysteresis/commands/setHysteresis")),
                        new CommandDescriptor("setHysteresisSwitchOnValue", true, List.of(new NumericParamDescriptor(true, "hysteresis", 1.0, 10.0, 0.5)), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.hysteresis/commands/setHysteresisSwitchOnValue")),
                        new CommandDescriptor("setHysteresisSwitchOffValue", true, List.of(new NumericParamDescriptor(true, "hysteresis", 1.0, 10.0, 0.5)), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.hysteresis/commands/setHysteresisSwitchOffValue"))
                        ),
                "value");
        Feature heatingDhwTemperatureTemp2 = new NumericSensorFeature("heating.dhw.temperature.temp2", Map.of("value", new DimensionalValue(Unit.CELSIUS, 47)),
                List.of(new CommandDescriptor("setTargetTemperature", true, List.of(new NumericParamDescriptor(true, "temperature", 10.0, 60.0, 1.0)), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.temp2/commands/setTargetTemperature"))),
                "value");

        List<Feature> ventilationOperatingModes = List.of(new StatusSensorFeature("ventilation.operating.modes.standby", null, false),
                new StatusSensorFeature("ventilation.operating.modes.ventilation", null, true),
                new StatusSensorFeature("ventilation.operating.modes.standard", null, false));
        Feature ventilationOperatingModesActive = new TextFeature("ventilation.operating.modes.active",
                "value", "ventilation", List.of(new CommandDescriptor("setMode", true, List.of(new EnumParamDescriptor(true, "mode", Set.of("standby", "standard", "ventilation"))), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/ventilation.operating.modes.active/commands/setMode"))));
        List<Feature> ventilationOperatingPrograms = List.of(
                new StatusSensorFeature("ventilation.operating.programs.levelOne", null, false),
                new StatusSensorFeature("ventilation.operating.programs.levelTwo", null, false),
                new StatusSensorFeature("ventilation.operating.programs.levelThree", null, true),
                new StatusSensorFeature("ventilation.operating.programs.levelFour", null, false),
                new StatusSensorFeature("ventilation.operating.programs.comfort", Map.of("active", BooleanValue.FALSE),
                List.of(
                        new CommandDescriptor("activate", true, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/ventilation.operating.programs.comfort/commands/activate")),
                        new CommandDescriptor("deactivate", false, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/ventilation.operating.programs.comfort/commands/deactivate"))
                )),
                new StatusSensorFeature("ventilation.operating.programs.eco", Map.of("active", BooleanValue.FALSE),
                List.of(
                        new CommandDescriptor("activate", true, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/ventilation.operating.programs.eco/commands/activate")),
                        new CommandDescriptor("deactivate", false, emptyList(), URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/ventilation.operating.programs.eco/commands/deactivate"))
                )),
                new StatusSensorFeature("ventilation.operating.programs.standby", Map.of("active", BooleanValue.FALSE,
                        "volumeFlow", new DimensionalValue(Unit.CUBIC_METRES_PER_HOUR, 0.0)))
        );
        Feature ventilationOperatingProgramsActive = new TextFeature("ventilation.operating.programs.active", "value", "levelThree");
        Feature ventilationOperatingProgramsHoliday = new DatePeriodFeature("ventilation.operating.programs.holiday", false, LocalDate.parse("2023-05-23"), LocalDate.parse("2023-05-30"));

        List<Feature> features = new ArrayList<>();
        features.addAll(programFeatures);
        features.addAll(operatingProgramsStatusSensorFeatures);
        features.addAll(heatingSensors);
        features.addAll(heatingCircuitsSensorsTemperatureSupply);
        features.addAll(heatingDHWOperatingModes);
        features.addAll(ventilationOperatingModes);
        features.addAll(ventilationOperatingPrograms);
        features.addAll(List.of(temperatureSensor, statisticsFeature, textFeature, statusFeature,
                                consumptionFeature, burnerFeature, curveFeature, holidayFeature,
                                heatingBufferTemperatureSensorTop,
                                heatingBufferTemperatureSensorMain,
                                heatingDhw,
                                heatingDhwCharging,
                                heatingDhwHotwaterStorageSensorTop,
                                heatingDhwHotwaterStorageSensorBottom,
                                heatingDhwTemperatureHysteresis,
                                heatingDhwTemperatureTemp2,
                                heatingDhwTemperatureHotWaterStorage, operatingModesActive,
                                heatingCircuitTemperatureLevels,
                                heatingDhwTemperatureMain,
                                heatingGasConsumptionDhw,
                                solarSensorsTemperatureCollector,
                                solarPowerProduction,
                                solarPowerCumulativeProduced,
                                solarSensorsTemperatureDHW,
                                solarPumpsCircuit,
                                heatingSolar,
                                heatingCircuit,
                                heatingCircuitName,
                                operatingModesDHW,
                                operatingModesDHWAndHeating,
                                operatingModesHeating,
                                operatingModesStandby,
                                operatingProgramsActive,
                                heatingCircuitsOperatingProgramsForcedLastFromSchedule,
                                heatingCircuitsZoneMode,
                                heatingDHWHygiene,
                                heatingDHWOneTimeCharge,
                                heatingCompressors0,
                                heatingCompressors0Statistics,
                                ventilationOperatingModesActive,
                                ventilationOperatingProgramsActive,
                                ventilationOperatingProgramsHoliday));
        return when(vicareService.getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID))
                .thenReturn(features);
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
        xmlChannelTypeProvider = new XmlChannelTypeProvider();
    }

    @BeforeEach
    void setUp() throws IOException {
        componentContext = mock(ComponentContext.class);
        bundleContext = mock(BundleContext.class);
        configuration = new SimpleConfiguration(bundleContext);
        var myChannelTypeRegistry = new MockChannelTypeRegistry();
        this.channelTypeRegistry = myChannelTypeRegistry;
        doReturn(bundleContext).when(componentContext).getBundleContext();
        Bundle bundle = mock(Bundle.class);
        doReturn(bundle).when(bundleContext).getBundle();
        org.osgi.service.cm.Configuration config = mock(org.osgi.service.cm.Configuration.class);
        org.osgi.service.cm.Configuration persistedTokenStoreConfig = mock(org.osgi.service.cm.Configuration.class);
        doReturn(config).when(configurationAdmin).getConfiguration("com.qubular.openhab.binding.vicare.SimpleConfiguration");
        doReturn(new File("/home/openhab/userdata/cache/org.eclipse.osgi/123/data/captures")).when(bundleContext).getDataFile("captures");
        doReturn(persistedTokenStoreConfig).when(configurationAdmin).getConfiguration(PersistedTokenStore.TOKEN_STORE_PID);
        Dictionary<String, Object> ptsProps = new Hashtable<>();
        VicareChannelTypeProvider channelTypeProvider = new SimpleVicareChannelTypeProvider();
        myChannelTypeRegistry.addChannelTypeProvider(channelTypeProvider);
        doReturn(ptsProps).when(persistedTokenStoreConfig).getProperties();
//        doAnswer(i -> ChannelTypeBuilder.state(i.getArgument(0), "Label", "Number:Temperature").build()).when(channelTypeRegistry).getChannelType(any(ChannelTypeUID.class));
        when(vicareServiceProvider.getVicareConfiguration()).thenReturn(configuration);
        when(vicareServiceProvider.getVicareService()).thenReturn(vicareService);
        when(vicareServiceProvider.getBindingVersion()).thenReturn("3.3.0");
        when(vicareServiceProvider.getThingRegistry()).thenReturn(thingRegistry);
        when(vicareServiceProvider.getBundleContext()).thenReturn(bundleContext);
        when(vicareServiceProvider.getConfigurationAdmin()).thenReturn(configurationAdmin);
        when(vicareServiceProvider.getChannelTypeRegistry()).thenReturn(myChannelTypeRegistry);
        when(vicareServiceProvider.getChannelTypeProvider()).thenReturn(channelTypeProvider);
        FeatureService featureService = new CachedFeatureService(vicareService);
        when(vicareServiceProvider.getFeatureService()).thenReturn(featureService);
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
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannel(heatingThing.thingCaptor, "heating_dhw_sensors_temperature_outlet");
        assertEquals("heating_dhw_sensors_temperature_outlet", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.outlet", channel.getProperties().get(PROPERTY_FEATURE_NAME));
        assertEquals("Number:Temperature", channel.getAcceptedItemType());

        Channel dhwHotWaterStorageTempChannel = findChannel(heatingThing.thingCaptor, "heating_dhw_sensors_temperature_hotWaterStorage");
        assertEquals("heating_dhw_sensors_temperature_hotWaterStorage", dhwHotWaterStorageTempChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.hotWaterStorage", dhwHotWaterStorageTempChannel.getProperties().get(PROPERTY_FEATURE_NAME));
        assertEquals("Number:Temperature", dhwHotWaterStorageTempChannel.getAcceptedItemType());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(27.3, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);

        heatingThing.handler.handleCommand(dhwHotWaterStorageTempChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(dhwHotWaterStorageTempChannel.getUID()), stateCaptor.capture());
        assertEquals(54.3, ((DecimalType) stateCaptor.getValue()).doubleValue(), 0.01);

        channel = findChannel(heatingThing.thingCaptor, "heating_dhw_sensors_temperature_outlet_status");
        assertEquals("heating_dhw_sensors_temperature_outlet_status", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.outlet", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new StringType("connected"), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_dhw_sensors_temperature_hotWaterStorage_status");
        assertEquals("heating_dhw_sensors_temperature_hotWaterStorage_status", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.sensors.temperature.hotWaterStorage", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new StringType("connected"), stateCaptor.getValue());
    }

    @Test
    public void thingRefreshSetsDeviceStatusOffline_whenVicareServiceReportsDeviceOffline() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseDisconnectedHeatingThing();
        verify(heatingThing.callback, timeout(1000)).statusUpdated(eq(heatingThing.thingCaptor.getValue()), argThat((ThingStatusInfo tsi) -> tsi.getStatus() == ThingStatus.ONLINE));
        Channel channel = findChannelNoVerify(heatingThing.thingCaptor,
                                                  "heating_circuits_1_operating_programs_active_value");
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.bridgeCallback, after(1000).never()).statusUpdated(any(Thing.class), argThat((ThingStatusInfo tsi)->tsi.getStatus() == ThingStatus.OFFLINE));

        ThingStatusInfo expectedStatus = new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to communicate with device: API returned 400:DEVICE_COMMUNICATION_ERROR - null - 404:GATEWAY_OFFLINE");
        verify(heatingThing.callback).statusUpdated(heatingThing.thingCaptor.getValue(), expectedStatus);
    }

    @Test
    public void supportsHeatingCircuitOperatingProgramsActive() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_1_operating_programs_active_value");
        assertEquals("heating.circuits.1.operating.programs.active", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType opProgramChannelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Heating Circuit 1 Active Operating Program", opProgramChannelType.getLabel());
        assertEquals("Shows the current active operating program on the device for Heating Circuit 1 (read-only)", opProgramChannelType.getDescription());
        assertEquals("String", opProgramChannelType.getItemType());
        assertTrue(opProgramChannelType.getState().isReadOnly());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("normalHeating"), stateCaptor.getValue());
    }

    static Stream<Arguments> source_heatingCircuitsOperatingPrograms() {
        return Stream.of(
                Arguments.of("normal", 21, OnOffType.ON, "Heating Circuit 0 Normal Operating Program Temperature", "Shows the Normal operating program target temperature for heating circuit 0 (read/write)", "Heating Circuit 0 Normal Operating Program Active", "Shows whether the Normal operating program is active for heating circuit 0 (read-only)", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normal/commands/setTemperature"), false, false, false),
                Arguments.of("reduced", 12, OnOffType.OFF, "Heating Circuit 0 Reduced Operating Program Temperature", "Shows the Reduced operating program target temperature for heating circuit 0 (read/write)", "Heating Circuit 0 Reduced Operating Program Active", "Shows whether the Reduced operating program is active for heating circuit 0 (read-only)", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.reduced/commands/setTemperature"), false, false, false),
                Arguments.of("comfort", 22, OnOffType.OFF, "Heating Circuit 0 Comfort Operating Program Temperature", "Shows the Comfort operating program target temperature for heating circuit 0 (read/write)", "Heating Circuit 0 Comfort Operating Program Active", "Shows whether the Comfort operating program is active for heating circuit 0 (read/write)", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfort/commands/setTemperature"), true, false, false),
                Arguments.of("comfort", 22, OnOffType.OFF, "Heating Circuit 0 Comfort Operating Program Temperature", "Shows the Comfort operating program target temperature for heating circuit 0 (read/write)", "Heating Circuit 0 Comfort Operating Program Active", "Shows whether the Comfort operating program is active for heating circuit 0 (read/write)", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfort/commands/setTemperature"), true, false, true),
                Arguments.of("reducedHeating", 18, OnOffType.OFF, "Heating Circuit 0 Reduced Heating Operating Program Temperature", "Shows the Reduced Heating operating program target temperature for heating circuit 0 (read/write)", "Heating Circuit 0 Reduced Heating Operating Program Active", "Shows whether the Reduced Heating operating program is active for heating circuit 0 (read-only)", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.reducedHeating/commands/setTemperature"), false, false, false),
                Arguments.of("normalHeating", 21, OnOffType.ON, "Heating Circuit 0 Normal Heating Operating Program Temperature", "Shows the Normal Heating operating program target temperature for heating circuit 0 (read/write)", "Heating Circuit 0 Normal Heating Operating Program Active", "Shows whether the Normal Heating operating program is active for heating circuit 0 (read-only)", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normalHeating/commands/setTemperature"), false, false, false),
                Arguments.of("comfortHeating", 22, OnOffType.OFF, "Heating Circuit 0 Comfort Heating Operating Program Temperature", "Shows the Comfort Heating operating program target temperature for heating circuit 0 (read/write)", "Heating Circuit 0 Comfort Heating Operating Program Active", "Shows whether the Comfort Heating operating program is active for heating circuit 0 (read-only)", 3.0, 37.0, URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.comfortHeating/commands/setTemperature"), false, false, false),
                Arguments.of("eco", 21, OnOffType.OFF, "Heating Circuit 0 Eco Operating Program Temperature", "Shows the Eco operating program target temperature for heating circuit 0 (read-only)", "Heating Circuit 0 Eco Operating Program Active", "Shows whether the Eco operating program is active for heating circuit 0 (read-only)", null, null, null, false, false, false),
                Arguments.of("external", 0, OnOffType.OFF, "Heating Circuit 0 External Operating Program Temperature", "Shows the External operating program target temperature for heating circuit 0 (read-only)", "Heating Circuit 0 External Operating Program Active", "Shows whether the External operating program is active for heating circuit 0 (read-only)", null, null, null, false, false, false)
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
                                                        URI expectedURI,
                                                        boolean activateEnabled,
                                                        boolean deactivateEnabled,
                                                        boolean simulateFailure) throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_operating_programs_" + featureSuffix);
        assertEquals("heating.circuits.0.operating.programs." + featureSuffix, channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType opProgramChannelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedTemperatureLabel, opProgramChannelType.getLabel());
        assertEquals(expectedTemperatureDescription, opProgramChannelType.getDescription());
        assertEquals("Heating", opProgramChannelType.getCategory());
        assertEquals("Number:Temperature", opProgramChannelType.getItemType());

        Channel activeChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_operating_programs_" + featureSuffix + "_active");
        assertEquals("heating.circuits.0.operating.programs." + featureSuffix, activeChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType opProgramActiveChannelType = channelTypeRegistry.getChannelType(activeChannel.getChannelTypeUID());
        assertEquals(expectedActiveLabel, opProgramActiveChannelType.getLabel());
        assertEquals(expectedActiveDescription, opProgramActiveChannelType.getDescription());
        assertEquals("Switch", opProgramActiveChannelType.getItemType());
        assertEquals(!(activateEnabled || deactivateEnabled), opProgramActiveChannelType.getState().isReadOnly());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        heatingThing.inOrder.verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedTemperature, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);

        heatingThing.handler.handleCommand(activeChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(activeChannel.getUID()), stateCaptor.capture());
        assertEquals(expectedActive, stateCaptor.getValue());

        if (expectedURI != null) {
            assertEquals(BigDecimal.valueOf(expectedMin), opProgramChannelType.getState().getMinimum());
            assertEquals(BigDecimal.valueOf(expectedMax), opProgramChannelType.getState().getMaximum());
            assertEquals(BigDecimal.valueOf(1.0), opProgramChannelType.getState().getStep());
            assertFalse(opProgramChannelType.getState().isReadOnly());
            heatingThing.handler.handleCommand(channel.getUID(), QuantityType.valueOf("15  °C"));
            heatingThing.inOrder.verify(vicareService, timeout(1000)).sendCommand(expectedURI, Map.of("targetTemperature", 15.0));
        }

        if (simulateFailure) {
            doThrow(new IOException("Simulated failure")).when(vicareService).sendCommand(expectedURI.resolve("activate"), emptyMap());
        }

        if (activateEnabled) {
            assertEquals(AutoUpdatePolicy.VETO, opProgramActiveChannelType.getAutoUpdatePolicy());
            heatingThing.handler.handleCommand(activeChannel.getUID(), OnOffType.ON);
            heatingThing.inOrder.verify(vicareService, timeout(1000)).sendCommand(expectedURI.resolve("activate"), emptyMap());
            if (simulateFailure) {
                heatingThing.inOrder.verify(heatingThing.callback, timeout(1000).times(0)).stateUpdated(eq(activeChannel.getUID()), any(State.class));
            } else {
                heatingThing.inOrder.verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(activeChannel.getUID()), stateCaptor.capture());
                assertEquals(OnOffType.ON, stateCaptor.getValue());
            }
        }
    }

    @Test
    public void initializeDeviceHandlerCreatesConsumptionChannels() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Map<String, Channel> channelsById = heatingThing.thingCaptor.getAllValues().stream()
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
                    heatingThing.handler.handleCommand(c.getUID(), RefreshType.REFRESH);
                    ArgumentCaptor<State> stateCaptor = forClass(State.class);
                    verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(c.getUID()), stateCaptor.capture());
                    assertEquals(expectedValues.get(c.getChannelTypeUID().getId()), ((QuantityType<?>)stateCaptor.getValue()).doubleValue(), 0.01);
        });
    }


    @Test
    public void supportsHeatingBurnersStatistics() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannel(heatingThing.thingCaptor, "heating_burners_0_statistics_starts");
        assertEquals("heating_burners_statistics_starts", channel.getChannelTypeUID().getId());
        assertEquals("heating.burners.0.statistics", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(5.0, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);
    }

    @Test
    public void initializeDeviceHandlerCreatesTextChannel() throws AuthenticationException, IOException, InterruptedException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannel(heatingThing.thingCaptor, "device_serial_value");
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), eq(StringType.valueOf("7723181102527121")));
    }

    @Test
    public void supportsWritableEnumProperty() throws AuthenticationException, IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel enumChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_operating_modes_active_value");
        ChannelType enumChannelType = channelTypeRegistry.getChannelType(enumChannel.getChannelTypeUID());
        assertEquals("Heating Circuit 0 Active Operating Mode", enumChannelType.getLabel());
        assertEquals("Shows the current active operating mode on the device for Heating Circuit 0 (read/write)", enumChannelType.getDescription());
        assertEquals("heating.circuits.0.operating.modes.active", enumChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Optional<Class<? extends ThingHandlerService>> descriptionProviderClass = heatingThing.handler.getServices().stream().filter(
                DynamicCommandDescriptionProvider.class::isAssignableFrom).findAny();
        assertTrue(descriptionProviderClass.isPresent());
        ThingHandlerService thingHandlerService = descriptionProviderClass.get().getConstructor().newInstance();
        thingHandlerService.setThingHandler(heatingThing.handler);
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


        heatingThing.handler.handleCommand(enumChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(enumChannel.getUID()), stateCaptor.capture());

        assertEquals(new StringType("dhw"), stateCaptor.getValue());

        heatingThing.handler.handleCommand(enumChannel.getUID(), new StringType("heating"));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        heatingThing.inOrder.verify(vicareService, timeout(1000)).sendCommand(eq(SET_MODE_URI), eq(Map.of("mode", "heating")));
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
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel slopeChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_heating_curve_slope");
        assertNotNull(slopeChannel);
        ChannelType slopeChannelType = channelTypeRegistry.getChannelType(slopeChannel.getChannelTypeUID());
        assertEquals("Heating Circuit 0 Heating Curve Slope", slopeChannelType.getLabel());
        assertEquals("Shows the value for slope of the heating curve for Heating Circuit 0 (read-only)", slopeChannelType.getDescription());
        assertEquals("heating.circuits.0.heating.curve", slopeChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel shiftChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_heating_curve_shift");
        assertNotNull(shiftChannel);
        ChannelType shiftChannelType = channelTypeRegistry.getChannelType(shiftChannel.getChannelTypeUID());
        assertEquals("Heating Circuit 0 Heating Curve Shift", shiftChannelType.getLabel());
        assertEquals("Shows the value for shift of the heating curve for Heating Circuit 0 (read-only)", shiftChannelType.getDescription());
        assertEquals("heating.circuits.0.heating.curve", shiftChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(slopeChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(slopeChannel.getUID()), stateCaptor.capture());
        assertEquals(1.6, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);

        heatingThing.handler.handleCommand(shiftChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(shiftChannel.getUID()), stateCaptor.capture());
        assertEquals(-4.0, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);
    }

    @Test
    public void initializeDeviceHandlerCreatesStatusSensor() throws AuthenticationException, IOException, InterruptedException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_circulation_pump_status");
        assertNotNull(channel);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("heating.circuits.0.circulation.pump", channel.getProperties().get(PROPERTY_FEATURE_NAME));
        assertEquals("Heating Circuit 0 Circulation Pump Status", channelType.getLabel());
        assertEquals("Shows the state of the circulation pump (on, off) for heating circuit 0 (read-only)", channelType.getDescription());

        Channel burnerChannel = findChannel(heatingThing.thingCaptor, "heating_burners_0_active");
        assertNotNull(burnerChannel);

        assertEquals("heating_burners_active", burnerChannel.getChannelTypeUID().getId());
        assertEquals("heating.burners.0", burnerChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel heatingDhwActiveChannel = findChannel(heatingThing.thingCaptor, "heating_dhw_active");
        assertEquals("heating_dhw_active", heatingDhwActiveChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw", heatingDhwActiveChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel heatingDhwStatusChannel = findChannel(heatingThing.thingCaptor, "heating_dhw_status");
        assertEquals("heating_dhw_status", heatingDhwStatusChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw", heatingDhwStatusChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("on"), stateCaptor.getValue());

        heatingThing.handler.handleCommand(burnerChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(burnerChannel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());

        heatingThing.handler.handleCommand(heatingDhwActiveChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(heatingDhwActiveChannel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());

        heatingThing.handler.handleCommand(heatingDhwStatusChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(heatingDhwStatusChannel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("on"), stateCaptor.getValue());
        heatingThing.inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
    }

    private ThingHandlerCallback createBridgeHandler(Bridge bridge) {
        VicareBridgeHandler bridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        bridgeHandler.setCallback(callback);
        when(bridge.getHandler()).thenReturn(bridgeHandler);
        return callback;
    }

    @Test
    public void supportsHeatingCircuit() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_name");
        assertNotNull(channel);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Heating Circuit 0 Name", channelType.getLabel());
        assertEquals("Shows the name given for Heating Circuit 0 (read-only)", channelType.getDescription());
        assertEquals("heating.circuits.0", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("circuitName"), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCircuitName() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_name_name");
        assertNotNull(channel);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Heating Circuit 0 Name", channelType.getLabel());
        assertEquals("Shows the name given for Heating Circuit 0 (read-only)", channelType.getDescription());
        assertEquals("heating.circuits.0.name", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("circuitNameName"), stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingCircuitsOperatingModes() {
        return Stream.of(
                Arguments.of("dhw", OnOffType.OFF, "Heating Circuit 1 DHW Operating Mode Active", "Shows whether the domestic hot water (DHW) only operating mode is active for Heating Circuit 1. (read-only)"),
                Arguments.of("dhwAndHeating", OnOffType.ON, "Heating Circuit 1 DHW And Heating Operating Mode Active", "Shows whether the domestic hot water (DHW) and heating operating mode is active for Heating Circuit 1. (read-only)"),
                Arguments.of("standby", OnOffType.OFF, "Heating Circuit 1 Standby Operating Mode Active", "Shows whether the Standby operating mode is active now for Heating Circuit 1. In this mode, the device will only start heating to protect installation from frost. Other commands, e.g. charging of DHW (oneTimeCharge), are still executable while this operating mode is active. (read-only)"),
                Arguments.of("heating", OnOffType.OFF, "Heating Circuit 1 Heating Operating Mode Active", "Shows whether the Heating operating mode is active for Heating Circuit 1. (read-only)")
        );
    }

    @ParameterizedTest()
    @MethodSource("source_heatingCircuitsOperatingModes")
    public void supportsHeatingCircuitOperatingMode(String suffix,
                                                    OnOffType expectedActive,
                                                    String expectedLabel,
                                                    String expectedDescription) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, String.format("heating_circuits_1_operating_modes_%s_active", suffix));
        assertNotNull(channel);
        assertEquals(String.format("heating.circuits.1.operating.modes.%s", suffix), channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedActive, stateCaptor.getValue());
    }

    private void registerAndInitialize(ThingHandler handler) {
        handler.initialize();
    }

    @Test
    public void supportsHeatingCircuitOperatingProgramsForcedLastFromSchedule() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_1_operating_programs_forcedLastFromSchedule_active");
        assertNotNull(channel);
        assertEquals("heating.circuits.1.operating.programs.forcedLastFromSchedule", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Heating Circuit 1 Extended Heating Active", channelType.getLabel());
        assertEquals("If activated, the last program activated by the schedule will be sustained until this feature is deactivated. (read-only)", channelType.getDescription());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingCircuitsOperatingProgramsStatusSensorFeatures() {
        return Stream.of(
                Arguments.of("standby", 1, OnOffType.OFF, "Heating Circuit 1 Standby Operating Program Active", "Shows whether the Standby operating program is active for heating circuit 1 (read-only)", null, null, null, null),
                Arguments.of("summerEco", 1, OnOffType.OFF, "Heating Circuit 1 Summer Eco Operating Program Active", "Shows whether the Summer Eco operating program is active for heating circuit 1 (read-only)", null, null, null, null),
                Arguments.of("fixed", 1, OnOffType.OFF, "Heating Circuit 1 Fixed Operating Program Active", "Shows whether the Fixed operating program is active for heating circuit 1 (read-only)", null, null, null, null),
                Arguments.of("normalEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Normal Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Normal operating program is scheduled on heating circuit 1 (read-only)",
                             "Heating Circuit 1 Normal Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Normal Energy Saving Operating Program Demand", "heating"),
                Arguments.of("reducedEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Reduced Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Reduced operating program is scheduled on heating circuit 1 (read-only)",
                             "Heating Circuit 1 Reduced Energy Saving Operating Program Reason", "unknown",
                             "Heating Circuit 1 Reduced Energy Saving Operating Program Demand", "heating"),
                Arguments.of("comfortEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Comfort Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Comfort operating program is scheduled on heating circuit 1 (read-only)",
                             "Heating Circuit 1 Comfort Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Comfort Energy Saving Operating Program Demand", "heating"),
                Arguments.of("normalCoolingEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Normal Cooling Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Normal Cooling operating program is scheduled on heating circuit 1 (read-only)",
                             "Heating Circuit 1 Normal Cooling Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Normal Cooling Energy Saving Operating Program Demand", "cooling"),
                Arguments.of("reducedCoolingEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Reduced Cooling Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Reduced Cooling operating program is scheduled on heating circuit 1 (read-only)",
                             "Heating Circuit 1 Reduced Cooling Energy Saving Operating Program Reason", "summerEco",
                             "Heating Circuit 1 Reduced Cooling Energy Saving Operating Program Demand", "cooling"),
                Arguments.of("comfortCoolingEnergySaving", 1, OnOffType.OFF, "Heating Circuit 1 Comfort Cooling Energy Saving Operating Program Active", "Shows whether the device is currently in the Energy Saving operating mode while the Comfort Cooling operating program is scheduled on heating circuit 1 (read-only)",
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
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_" + heatingCircuit + "_operating_programs_" + suffix + "_active");
        assertNotNull(channel);
        assertEquals("heating.circuits." + heatingCircuit + ".operating.programs." + suffix, channel.getProperties().get(PROPERTY_FEATURE_NAME));

        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());
        assertTrue(channelType.getState().isReadOnly());
        assertEquals("Switch", channelType.getItemType());

        if (expectedReason != null) {
            Channel reasonChannel = findChannelNoVerify(heatingThing.thingCaptor, String.format("heating_circuits_%s_operating_programs_%s_reason", heatingCircuit, suffix));
            ChannelType reasonChannelType = channelTypeRegistry.getChannelType(reasonChannel.getChannelTypeUID());
            assertEquals(expectedReasonLabel, reasonChannelType.getLabel());
            assertEquals("String", reasonChannelType.getItemType());

            heatingThing.handler.handleCommand(reasonChannel.getUID(), RefreshType.REFRESH);
            ArgumentCaptor<State> reasonCaptor = ArgumentCaptor.forClass(State.class);
            verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(reasonChannel.getUID()), reasonCaptor.capture());
            assertEquals(StringType.valueOf(expectedReason), reasonCaptor.getValue());
            assertTrue(reasonChannelType.getState().isReadOnly());
        }

        if (expectedDemand != null) {
            Channel demandChannel = findChannelNoVerify(heatingThing.thingCaptor, String.format("heating_circuits_%s_operating_programs_%s_demand", heatingCircuit, suffix));
            ChannelType demandChannelType = channelTypeRegistry.getChannelType(demandChannel.getChannelTypeUID());
            assertEquals(expectedDemandLabel, demandChannelType.getLabel());
            assertEquals("String", demandChannelType.getItemType());

            heatingThing.handler.handleCommand(demandChannel.getUID(), RefreshType.REFRESH);
            ArgumentCaptor<State> demandCaptor = ArgumentCaptor.forClass(State.class);
            verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(demandChannel.getUID()), demandCaptor.capture());
            assertEquals(StringType.valueOf(expectedDemand), demandCaptor.getValue());
            assertTrue(demandChannelType.getState().isReadOnly());
        }

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedActive, stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingCircuitsSensorsTemperatureSupply() {
        return Stream.of(Arguments.of("heating_circuits_1_sensors_temperature_supply", "heating.circuits.1.sensors.temperature.supply",
                                      "Heating Circuit 1 Supply Temperature", "Shows the value of the supply temperature sensor for Heating Circuit 1 (read-only)", DecimalType.valueOf("24.6"),
                                      "Heating Circuit 1 Supply Temperature Sensor Status", "Shows the status of the supply temperature sensor for Heating Circuit 1 (read-only)", "connected"),
                         Arguments.of("heating_primaryCircuit_sensors_temperature_supply", "heating.primaryCircuit.sensors.temperature.supply",
                                      "Primary Circuit Supply Temperature", "Shows the temperature value of the primary source's supply-temperature sensor of the heat pump", DecimalType.valueOf("17.4"),
                                      "Primary Circuit Supply Temperature Sensor Status", "Shows the status of the primary source's supply-temperature sensor", "connected"),
                         Arguments.of("heating_secondaryCircuit_sensors_temperature_supply", "heating.secondaryCircuit.sensors.temperature.supply",
                                      "Secondary Circuit Supply Temperature", "Shows the temperature value of the secondary source's supply-temperature sensor of the heat pump", DecimalType.valueOf("34.1"),
                                      "Secondary Circuit Supply Temperature Sensor Status", "Shows the status of the secondary source's supply-temperature sensor", "connected"),
                         Arguments.of("heating_circuits_0_sensors_temperature_room", "heating.circuits.0.sensors.temperature.room",
                                      "Heating Circuit 0 Room Temperature", "Shows the temperature value of the room temperature sensor for Heating Circuit 0 (read-only)", DecimalType.valueOf("24.1"),
                                      "Heating Circuit 0 Room Temperature Sensor Status", "Shows the status of the room temperature sensor for Heating Circuit 0 (read-only)", "connected")

        );
    }

    @MethodSource("source_heatingCircuitsSensorsTemperatureSupply")
    @ParameterizedTest
    public void supportsHeatingCircuitSensorsTemperatureSupply(String channelId, String featureName, String expectedLabel, String expectedDescription, DecimalType expectedTemp, String expectedStatusLabel, String expectedStatusDescription, String expectedStatus) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, channelId);
        assertNotNull(channel);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());
        assertEquals(featureName, channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedTemp, stateCaptor.getValue());

        channel = findChannelNoVerify(heatingThing.thingCaptor, String.format("%s_status", channelId));
        assertNotNull(channel);
        channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedStatusLabel, channelType.getLabel());
        assertEquals(expectedStatusDescription, channelType.getDescription());
        assertEquals(featureName, channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf(expectedStatus), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCircuitTemperatureLevels() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel minChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_temperature_levels_min");
        assertNotNull(minChannel);
        assertEquals("heating.circuits.0.temperature.levels", minChannel.getProperties().get(PROPERTY_FEATURE_NAME));
        ChannelType minChannelType = channelTypeRegistry.getChannelType(minChannel.getChannelTypeUID());
        assertEquals("Heating Circuit 0 Minimum Temperature Level", minChannelType.getLabel());
        assertEquals("Shows the lower limit of the supply temperature for Heating Circuit 0 (read/write)", minChannelType.getDescription());

        Channel maxChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_0_temperature_levels_max");
        assertNotNull(maxChannel);
        assertEquals("heating.circuits.0.temperature.levels", maxChannel.getProperties().get(PROPERTY_FEATURE_NAME));
        ChannelType maxChannelType = channelTypeRegistry.getChannelType(maxChannel.getChannelTypeUID());
        assertEquals("Heating Circuit 0 Maximum Temperature Level", maxChannelType.getLabel());
        assertEquals("Shows the upper limit of the supply temperature for Heating Circuit 0 (read/write)", maxChannelType.getDescription());

        heatingThing.handler.handleCommand(minChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(minChannel.getUID()), stateCaptor.capture());
        assertEquals(DecimalType.valueOf("20"), stateCaptor.getValue());
        heatingThing.handler.handleCommand(maxChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(maxChannel.getUID()), stateCaptor.capture());
        assertEquals(DecimalType.valueOf("45"), stateCaptor.getValue());

        heatingThing.handler.handleCommand(maxChannel.getUID(), QuantityType.valueOf("46  °C"));
        heatingThing.inOrder.verify(vicareService, timeout(1000)).sendCommand(SET_LEVEL_MAX_URI, Map.of("temperature", 46.0));
    }

    @Test
    public void supportsHeatingCircuitZoneMode() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_circuits_1_zone_mode_active");
        assertNotNull(channel);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Heating Circuit 1 Zone Mode Active", channelType.getLabel());
        assertEquals("Shows if a zone is connected to Heating Circuit 1 (read-only)", channelType.getDescription());
        assertEquals("heating.circuits.1.zone.mode", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCompressors() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel phaseChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_compressors_0_phase");
        assertNotNull(phaseChannel);
        ChannelType channelType = channelTypeRegistry.getChannelType(phaseChannel.getChannelTypeUID());
        assertEquals("Heating Compressor 0", channelType.getLabel());
        assertEquals("Shows whether compressor 0 is active (read-only)", channelType.getDescription());
        assertEquals("heating.compressors.0", phaseChannel.getProperties().get(PROPERTY_FEATURE_NAME));
        assertTrue(channelType.getState().isReadOnly());

        Channel activeChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_compressors_0_active");
        assertNotNull(activeChannel);
        ChannelType activeChannelType = channelTypeRegistry.getChannelType(activeChannel.getChannelTypeUID());
        assertEquals("Heating Compressor 0 Active", activeChannelType.getLabel());
        assertEquals("Shows whether compressor 0 is active (read-only)", activeChannelType.getDescription());
        assertEquals("Switch", activeChannelType.getItemType());
        assertTrue(activeChannelType.getState().isReadOnly());

        heatingThing.handler.handleCommand(phaseChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(phaseChannel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("ready"), stateCaptor.getValue());

        heatingThing.handler.handleCommand(activeChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(activeChannel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingCompressorsStatistics() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_compressors_0_statistics_starts");
        assertEquals("heating.compressors.0.statistics", channel.getProperties().get(PROPERTY_FEATURE_NAME));
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Heating Compressor 0 Starts", channelType.getLabel());
        assertEquals("Shows the number of starts of Heating Compressor 0 (read-only)", channelType.getDescription());
        assertEquals("Number", channelType.getItemType());
        assertTrue(channelType.getState().isReadOnly());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL,
                                                                              DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(177.0, ((DecimalType) stateCaptor.getValue()).doubleValue(), 0.01);

    }

    public static Stream<Arguments> source_heatingCompressorsStatistics_hours() {
        return Stream.of(Arguments.of("heating_compressors_0_statistics_hours", "heating.compressors.0.statistics", "Heating Compressor 0 Hours", "Shows the number of working hours of Heating Compressor 0 (read-only)", 29.0),
                         Arguments.of("heating_compressors_0_statistics_hoursLoadClassOne", "heating.compressors.0.statistics", "Heating Compressor 0 Hours Load Class One", "(read-only)", 253),
                         Arguments.of("heating_compressors_0_statistics_hoursLoadClassTwo", "heating.compressors.0.statistics", "Heating Compressor 0 Hours Load Class Two", "(read-only)", 519),
                         Arguments.of("heating_compressors_0_statistics_hoursLoadClassThree", "heating.compressors.0.statistics", "Heating Compressor 0 Hours Load Class Three", "(read-only)", 1962),
                         Arguments.of("heating_compressors_0_statistics_hoursLoadClassFour", "heating.compressors.0.statistics", "Heating Compressor 0 Hours Load Class Four", "(read-only)", 257),
                         Arguments.of("heating_compressors_0_statistics_hoursLoadClassFive", "heating.compressors.0.statistics", "Heating Compressor 0 Hours Load Class Five", "(read-only)", 71)
                         );
    } 
    
    @MethodSource("source_heatingCompressorsStatistics_hours")
    @ParameterizedTest
    public void supportsHeatingCompressorsStatistics_hours(String channelName, String featureName, String expectedLabel, String expectedDescription, double expectedValue) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();
        Channel hoursChannel = findChannelNoVerify(heatingThing.thingCaptor, channelName);
        assertEquals(featureName, hoursChannel.getProperties().get(PROPERTY_FEATURE_NAME));
        ChannelType hoursChannelType = channelTypeRegistry.getChannelType(hoursChannel.getChannelTypeUID());
        assertEquals(expectedLabel, hoursChannelType.getLabel());
        assertEquals(expectedDescription, hoursChannelType.getDescription());
        assertEquals("Number", hoursChannelType.getItemType());
        assertTrue(hoursChannelType.getState().isReadOnly());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        heatingThing.handler.handleCommand(hoursChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(hoursChannel.getUID()), stateCaptor.capture());
        assertEquals(expectedValue, ((DecimalType)stateCaptor.getValue()).doubleValue(), 0.01);
    }

    @Test
    public void supportsHeatingDhwCharging() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_dhw_charging_active");
        assertNotNull(channel);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Switch", channelType.getItemType());
        assertEquals("DHW Charging Active", channelType.getLabel());
        assertEquals("Shows whether the hot water charging for the DHW storage is currently active.", channelType.getDescription());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingDhwHygiene() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannel(heatingThing.thingCaptor, "heating_dhw_hygiene_enabled");
        assertNotNull(channel);
        assertEquals("heating_dhw_hygiene_enabled", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.hygiene", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingDhwOneTimeCharge() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannel(heatingThing.thingCaptor, "heating_dhw_oneTimeCharge_active");
        assertNotNull(channel);
        assertEquals("heating_dhw_oneTimeCharge_active", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.oneTimeCharge", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_dhw_oneTimeCharge_activate");
        assertNotNull(channel);
        assertEquals("heating_dhw_oneTimeCharge_activate", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.oneTimeCharge", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), OnOffType.ON);
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/activate"), emptyMap());

        channel = findChannel(heatingThing.thingCaptor, "heating_dhw_oneTimeCharge_deactivate");
        assertNotNull(channel);
        assertEquals("heating_dhw_oneTimeCharge_deactivate", channel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.oneTimeCharge", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), OnOffType.ON);
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/deactivate"), emptyMap());
    }

    public static Stream<Arguments> source_heatingDHWOperatingModes() {
        return Stream.of(Arguments.of("heating.dhw.operating.modes.off", "heating_dhw_operating_modes_off_active", "DHW Off Operating Mode Active", OnOffType.OFF),
                         Arguments.of("heating.dhw.operating.modes.comfort", "heating_dhw_operating_modes_comfort_active", "DHW Comfort Operating Mode Active", OnOffType.ON),
                         Arguments.of("heating.dhw.operating.modes.balanced", "heating_dhw_operating_modes_balanced_active", "DHW Balanced Operating Mode Active", OnOffType.OFF),
                         Arguments.of("heating.dhw.operating.modes.eco", "heating_dhw_operating_modes_eco_active", "DHW Eco Operating Mode Active", OnOffType.OFF));
    }

    @ParameterizedTest
    @MethodSource("source_heatingDHWOperatingModes")
    public void supportsHeatingDhwOperatingModes(String featureName, String channelId,
                                                    String expectedLabel,
                                                    OnOffType expectedActive) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, channelId);
        assertNotNull(channel);
        assertEquals(featureName, channel.getProperties().get(PROPERTY_FEATURE_NAME));
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedActive, stateCaptor.getValue());
    }
    @Test
    public void supportsHeatingDhwOperatingModeActive() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, "heating_dhw_operating_modes_active_value");
        assertNotNull(channel);
        assertEquals("heating.dhw.operating.modes.active", channel.getProperties().get(PROPERTY_FEATURE_NAME));
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("DHW Active Operating Mode", channelType.getLabel());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new StringType("eco"), stateCaptor.getValue());

        heatingThing.handler.handleCommand(channel.getUID(), new StringType("comfort"));
        heatingThing.inOrder.verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.dhw.operating.modes.active/commands/setMode"), Map.of("mode", "comfort"));
    }

    @Test
    public void supportsHeatingDhwTemperatureMain() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel tempChannel = findChannel(heatingThing.thingCaptor, "heating_dhw_temperature_main");
        assertNotNull(tempChannel);
        assertEquals("heating_dhw_temperature_main", tempChannel.getChannelTypeUID().getId());
        assertEquals("heating.dhw.temperature.main", tempChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(tempChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(tempChannel.getUID()), stateCaptor.capture());
        assertEquals(DecimalType.valueOf("50.0"), stateCaptor.getValue());

        heatingThing.handler.handleCommand(tempChannel.getUID(), QuantityType.valueOf("51  °C"));
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
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannel(heatingThing.thingCaptor, "heating_circuits_0_operating_programs_holiday_active");
        assertNotNull(channel);
        assertEquals("heating_circuits_operating_programs_holiday_active", channel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.operating.programs.holiday", channel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel startChannel = findChannel(heatingThing.thingCaptor, "heating_circuits_0_operating_programs_holiday_start");
        assertNotNull(startChannel);

        assertEquals("heating_circuits_operating_programs_holiday_start", startChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.operating.programs.holiday", startChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        Channel endChannel = findChannel(heatingThing.thingCaptor, "heating_circuits_0_operating_programs_holiday_end");
        assertNotNull(startChannel);

        assertEquals("heating_circuits_operating_programs_holiday_end", endChannel.getChannelTypeUID().getId());
        assertEquals("heating.circuits.0.operating.programs.holiday", endChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());

        heatingThing.handler.handleCommand(startChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(startChannel.getUID()), stateCaptor.capture());
        var zoneString = ((DateTimeType)stateCaptor.getValue()).getZonedDateTime().getZone().getId();
        assertEquals(ZonedDateTime.parse("2022-12-23T00:00:00"+zoneString), ((DateTimeType)stateCaptor.getValue()).getZonedDateTime());

        heatingThing.handler.handleCommand(endChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, never()).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(endChannel.getUID()), stateCaptor.capture());
        zoneString = ((DateTimeType)stateCaptor.getValue()).getZonedDateTime().getZone().getId();
        assertEquals(ZonedDateTime.parse("2022-12-26T23:59:59.999999999"+zoneString), ((DateTimeType)stateCaptor.getValue()).getZonedDateTime());
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
    public void initializeBridgeSetsResponseCaptureFolderProperty() {
        Bridge bridge = vicareBridge();
        VicareBridgeHandler vicareBridgeHandler = new VicareBridgeHandler(vicareServiceProvider, bridge);
        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        vicareBridgeHandler.setCallback(callback);
        vicareBridgeHandler.initialize();

        verify(bridge).setProperty(PROPERTY_RESPONSE_CAPTURE_FOLDER, "/home/openhab/userdata/cache/org.eclipse.osgi/123/data/captures");
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

    public static Stream<Arguments> source_heatingSensorsTemperature() {
        return Stream.of(
                Arguments.of("heating_sensors_temperature_outside", "Outside Temperature", "Shows the temperature value of the outside temperature sensor", 15.8, "Outside Temperature Sensor Status", "Shows the status of the outside temperature sensor", "connected"),
                Arguments.of("heating_sensors_temperature_return", "Heating Return Temperature", "Shows the flow return temperature, i.e. water temperature on return to the boiler from the heating installation.", 34.1, "Heating Return Temperature Sensor Status", "Shows the flow return temperature sensor status", "connected")
        );
    }

    @MethodSource("source_heatingSensorsTemperature")
    @ParameterizedTest
    public void supportsHeatingSensorsTemperature(String channelId,
                                                  String expectedLabel,
                                                  String expectedDescription,
                                                  double expectedValue,
                                                  String expectedStatusLabel,
                                                  String expectedStatusDescription,
                                                  String expectedStatusValue) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, channelId);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());
        assertEquals("Number:Temperature", channelType.getItemType());
        assertEquals("Temperature", channelType.getCategory());
        assertTrue(channelType.getState().isReadOnly());

        Channel statusChannel = findChannelNoVerify(heatingThing.thingCaptor, String.format("%s_status", channelId));
        ChannelType statusChannelType = channelTypeRegistry.getChannelType(statusChannel.getChannelTypeUID());
        assertEquals(expectedStatusLabel, statusChannelType.getLabel());
        assertEquals(expectedStatusDescription, statusChannelType.getDescription());
        assertEquals("String", statusChannelType.getItemType());
        assertTrue(statusChannelType.getState().isReadOnly());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(expectedValue), stateCaptor.getValue());

        heatingThing.handler.handleCommand(statusChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(statusChannel.getUID()), stateCaptor.capture());
        assertEquals(new StringType(expectedStatusValue), stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingSensorsVolumetricFlow() {
        return Stream.of(
                Arguments.of("heating_sensors_volumetricFlow_allengra", "Heating Return Flow", "Shows the volumetric flow on the return.", 274, "Heating Return Flow Sensor Status", "Shows the volumetric flow sensor status", "connected")
        );
    }

    @MethodSource("source_heatingSensorsVolumetricFlow")
    @ParameterizedTest
    public void supportsHeatingSensorsVolumetricFlow(String channelId,
                                                     String expectedLabel,
                                                     String expectedDescription,
                                                     double expectedValue,
                                                     String expectedStatusLabel,
                                                     String expectedStatusDescription,
                                                     String expectedStatusValue) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, channelId);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());
        assertEquals("Number:VolumetricFlowRate", channelType.getItemType());
        assertEquals("Flow", channelType.getCategory());
        assertTrue(channelType.getState().isReadOnly());

        Channel statusChannel = findChannelNoVerify(heatingThing.thingCaptor, String.format("%s_status", channelId));
        ChannelType statusChannelType = channelTypeRegistry.getChannelType(statusChannel.getChannelTypeUID());
        assertEquals(expectedStatusLabel, statusChannelType.getLabel());
        assertEquals(expectedStatusDescription, statusChannelType.getDescription());
        assertEquals("String", statusChannelType.getItemType());
        assertTrue(statusChannelType.getState().isReadOnly());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(expectedValue), stateCaptor.getValue());

        heatingThing.handler.handleCommand(statusChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(statusChannel.getUID()), stateCaptor.capture());
        assertEquals(new StringType(expectedStatusValue), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingSolar() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(heatingThing.thingCaptor, "heating_solar_active");
        assertEquals("heating_solar_active", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.ON, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingSolarSensorsTemperatureCollector() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel tempChannel = findChannel(heatingThing.thingCaptor, "heating_solar_sensors_temperature_collector");
        assertNotNull(tempChannel);
        assertEquals("heating_solar_sensors_temperature_collector", tempChannel.getChannelTypeUID().getId());
        assertEquals("heating.solar.sensors.temperature.collector", tempChannel.getProperties().get(PROPERTY_FEATURE_NAME));

        heatingThing.handler.handleCommand(tempChannel.getUID(), RefreshType.REFRESH);
        heatingThing.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(tempChannel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(35.4), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingSolarPowerCumulativeProduced() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_cumulativeProduced");
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals("Solar Power Cumulative Production", channelType.getLabel());
        assertEquals("Shows the cumulative value of power produced by solar thermal.", channelType.getDescription());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(14091), stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingGasConsumptionDhw() {
        return Stream.of(
                Arguments.of("heating_gas_consumption_dhw_currentDay", "%.1f %unit%", new QuantityType(0.7, Units.KILOWATT_HOUR), "Number:Energy"),
                Arguments.of("heating_gas_consumption_dhw_previousDay", "%.1f %unit%", new QuantityType(1.3, Units.KILOWATT_HOUR), "Number:Energy"),
                Arguments.of("heating_gas_consumption_dhw_currentWeek", "%.1f %unit%", new QuantityType(0.7, tech.units.indriya.unit.Units.CUBIC_METRE), "Number:Volume"),
                Arguments.of("heating_gas_consumption_dhw_previousWeek", "%.1f %unit%", new QuantityType(7.6, tech.units.indriya.unit.Units.CUBIC_METRE), "Number:Volume")
         );
    }

    @MethodSource("source_heatingGasConsumptionDhw")
    @ParameterizedTest
    public void supportsHeatingGasConsumptionDhw_withUnitVariation(String channelId, String expectedPattern, State expectedState, String expectedItemType) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(heatingThing.thingCaptor, channelId);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedPattern, channelType.getState().getPattern());
        assertEquals(expectedItemType, channel.getAcceptedItemType());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedState, stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingSolarPowerProduction() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_currentDay");
        assertEquals("heating_solar_power_production_currentDay", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(0, Units.KILOWATT_HOUR), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_previousDay");
        assertEquals("heating_solar_power_production_previousDay", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(11.4, Units.KILOWATT_HOUR), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_currentWeek");
        assertEquals("heating_solar_power_production_currentWeek", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(31, Units.KILOWATT_HOUR), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_previousWeek");
        assertEquals("heating_solar_power_production_previousWeek", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(58.3, Units.KILOWATT_HOUR), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_currentMonth");
        assertEquals("heating_solar_power_production_currentMonth", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(247.8, Units.KILOWATT_HOUR), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_previousMonth");
        assertEquals("heating_solar_power_production_previousMonth", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(355.5, Units.KILOWATT_HOUR), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_currentYear");
        assertEquals("heating_solar_power_production_currentYear", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(4250.6, Units.KILOWATT_HOUR), stateCaptor.getValue());

        channel = findChannel(heatingThing.thingCaptor, "heating_solar_power_production_previousYear");
        assertEquals("heating_solar_power_production_previousYear", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new QuantityType(0, Units.KILOWATT_HOUR), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingSolarSensorsTemperatureDHW() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(heatingThing.thingCaptor, "heating_solar_sensors_temperature_dhw");
        assertEquals("heating_solar_sensors_temperature_dhw", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(28.1), stateCaptor.getValue());
    }

    @Test
    public void supportsHeatingSolarPumpsCircuit() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        Channel channel = findChannel(heatingThing.thingCaptor, "heating_solar_pumps_circuit_status");
        assertEquals("heating_solar_pumps_circuit_status", channel.getChannelTypeUID().getId());
        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(StringType.valueOf("off"), stateCaptor.getValue());
    }

    public static Stream<Arguments> source_heatingBufferSensorsTemperature() {
        return Stream.of(
                Arguments.of("heating_buffer_sensors_temperature_top_status", "Heating Buffer Top Temperature Sensor Status", "notConnected",
                            null, null, null),
                Arguments.of("heating_buffer_sensors_temperature_main_status", "Heating Buffer Main Temperature Sensor Status", "connected",
                             "heating_buffer_sensors_temperature_main_value", "Heating Buffer Main Temperature Sensor Reading", new DecimalType("28.9"))
        );
    }
    
    @MethodSource("source_heatingBufferSensorsTemperature")
    @ParameterizedTest
    public void supportsHeatingBufferSensorsTemperature(String statusChannelId, String expectedStatusLabel, String expectedStatus, String temperatureChannelId, String expectedTemperatureLabel, State expectedTemperature) throws AuthenticationException, IOException {
        HeatingThing result = initialiseHeatingThing();

        Channel statusChannel = findChannelNoVerify(result.thingCaptor, statusChannelId);
        ChannelType statusChannelType = channelTypeRegistry.getChannelType(statusChannel.getChannelTypeUID());
        assertEquals(expectedStatusLabel, statusChannelType.getLabel());
        assertEquals("String", statusChannelType.getItemType());
        assertTrue(statusChannelType.getState().isReadOnly());

        result.handler.handleCommand(statusChannel.getUID(), RefreshType.REFRESH);
        result.inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        verify(result.callback, timeout(1000)).stateUpdated(eq(statusChannel.getUID()), eq(new StringType(expectedStatus)));

        if (temperatureChannelId != null) {
            Channel temperatureChannel = findChannelNoVerify(result.thingCaptor, temperatureChannelId);
            ChannelType temperatureChannelType = channelTypeRegistry.getChannelType(temperatureChannel.getChannelTypeUID());
            assertEquals(expectedTemperatureLabel, temperatureChannelType.getLabel());
            assertTrue(temperatureChannelType.getState().isReadOnly());
            assertEquals("Number:Temperature", temperatureChannelType.getItemType());

            result.handler.handleCommand(temperatureChannel.getUID(), RefreshType.REFRESH);
            verify(result.callback, timeout(1000)).stateUpdated(eq(temperatureChannel.getUID()), eq(expectedTemperature));
        }
    }

    public static Stream<Arguments> source_dhwSensorsTemperatureHotWaterStorage() {
        return Stream.of(
                Arguments.of("heating_dhw_sensors_temperature_hotWaterStorage_top", "Hot Water Storage Top Temperature",
                             "heating_dhw_sensors_temperature_hotWaterStorage_top_status", "Hot Water Storage Top Temperature Sensor Status",
                             44.4, "connected"),
                Arguments.of(null, null,
                             "heating_dhw_sensors_temperature_hotWaterStorage_bottom_status", "Hot Water Storage Bottom Temperature Sensor Status",
                             null, "notConnected")
        );
    }

    @MethodSource("source_dhwSensorsTemperatureHotWaterStorage")
    @ParameterizedTest
    public void supportsDhwSensorsTemperatureHotWaterStorage(String valueChannelId, String expectedValueLabel, String statusChannelId, String expectedStatusLabel, Double expectedValue, @Nullable String expectedStatusValue) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel valueChannel = findChannelNoVerify(heatingThing.thingCaptor, valueChannelId);
        if (valueChannelId != null) {
            ChannelType valueChannelType = channelTypeRegistry.getChannelType(valueChannel.getChannelTypeUID());
            assertEquals(expectedValueLabel, valueChannelType.getLabel());
            assertEquals("Number:Temperature", valueChannelType.getItemType());
            assertEquals("Temperature", valueChannelType.getCategory());
            assertTrue(valueChannelType.getState().isReadOnly());
        } else {
            assertNull(valueChannel);
        }

        Channel statusChannel = findChannelNoVerify(heatingThing.thingCaptor, statusChannelId);
        ChannelType statusChannelType = channelTypeRegistry.getChannelType(statusChannel.getChannelTypeUID());
        assertEquals(expectedStatusLabel, statusChannelType.getLabel());
        assertEquals("String", statusChannelType.getItemType());
        assertTrue(statusChannelType.getState().isReadOnly());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        heatingThing.handler.handleCommand(statusChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(statusChannel.getUID()), stateCaptor.capture());
        assertEquals(new StringType(expectedStatusValue), stateCaptor.getValue());

        if (expectedValue != null) {
            heatingThing.handler.handleCommand(valueChannel.getUID(), RefreshType.REFRESH);
            verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(valueChannel.getUID()), stateCaptor.capture());
            assertEquals(new DecimalType(expectedValue), stateCaptor.getValue());
        }
    }

    @Test
    public void supportsDhwTemperatureHysteresis() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel valueChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_dhw_temperature_hysteresis");
        ChannelType valueChannelType = channelTypeRegistry.getChannelType(valueChannel.getChannelTypeUID());
        assertEquals("DHW Hysteresis", valueChannelType.getLabel());
        assertEquals("Shows the hysteresis value of the Domestic Hot Water temperature in heat pumps. (read/write)", valueChannelType.getDescription());
        assertEquals("Number:Temperature", valueChannelType.getItemType());
        assertFalse(valueChannelType.getState().isReadOnly());
        assertEquals(new BigDecimal("1.0"), valueChannelType.getState().getMinimum());
        assertEquals(new BigDecimal("10.0"), valueChannelType.getState().getMaximum());
        assertEquals(new BigDecimal("0.5"), valueChannelType.getState().getStep());

        Channel switchOnChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_dhw_temperature_hysteresis_switchOnValue");
        ChannelType switchOnChannelType = channelTypeRegistry.getChannelType(switchOnChannel.getChannelTypeUID());
        assertEquals("DHW Hysteresis Switch On Value", switchOnChannelType.getLabel());
        assertEquals("Number:Temperature", switchOnChannelType.getItemType());
        assertFalse(switchOnChannelType.getState().isReadOnly());
        assertEquals(new BigDecimal("1.0"), switchOnChannelType.getState().getMinimum());
        assertEquals(new BigDecimal("10.0"), switchOnChannelType.getState().getMaximum());
        assertEquals(new BigDecimal("0.5"), switchOnChannelType.getState().getStep());

        Channel switchOffChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_dhw_temperature_hysteresis_switchOffValue");
        ChannelType switchOffChannelType = channelTypeRegistry.getChannelType(switchOffChannel.getChannelTypeUID());
        assertEquals("DHW Hysteresis Switch Off Value", switchOffChannelType.getLabel());
        assertEquals("Number:Temperature", switchOffChannelType.getItemType());
        assertFalse(switchOffChannelType.getState().isReadOnly());
        assertEquals(new BigDecimal("1.0"), switchOffChannelType.getState().getMinimum());
        assertEquals(new BigDecimal("10.0"), switchOffChannelType.getState().getMaximum());
        assertEquals(new BigDecimal("0.5"), switchOffChannelType.getState().getStep());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        heatingThing.handler.handleCommand(valueChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(valueChannel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(5), stateCaptor.getValue());

        heatingThing.handler.handleCommand(switchOnChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(switchOnChannel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(4), stateCaptor.getValue());

        heatingThing.handler.handleCommand(switchOffChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(switchOffChannel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(6), stateCaptor.getValue());

        heatingThing.handler.handleCommand(valueChannel.getUID(), new DecimalType(4.4));
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.hysteresis/commands/setHysteresis"), Map.of("hysteresis", 4.4));
        heatingThing.handler.handleCommand(switchOnChannel.getUID(), new DecimalType(4.4));
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.hysteresis/commands/setHysteresisSwitchOnValue"), Map.of("hysteresis", 4.4));
        heatingThing.handler.handleCommand(switchOffChannel.getUID(), new DecimalType(4.4));
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.hysteresis/commands/setHysteresisSwitchOffValue"), Map.of("hysteresis", 4.4));

    }

    @Test
    public void supportsDhwTemperatureTemp2() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel valueChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_dhw_temperature_temp2");
        ChannelType valueChannelType = channelTypeRegistry.getChannelType(valueChannel.getChannelTypeUID());
        assertEquals("DHW Temp2 Temperature", valueChannelType.getLabel());
        assertEquals("Shows the target value of the domestic hot water (DHW) Temp2 temperature. (read/write)", valueChannelType.getDescription());
        assertEquals("Number:Temperature", valueChannelType.getItemType());

        assertFalse(valueChannelType.getState().isReadOnly());
        assertEquals(new BigDecimal("10.0"), valueChannelType.getState().getMinimum());
        assertEquals(new BigDecimal("60.0"), valueChannelType.getState().getMaximum());
        assertEquals(new BigDecimal("1.0"), valueChannelType.getState().getStep());

        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        heatingThing.handler.handleCommand(valueChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(valueChannel.getUID()), stateCaptor.capture());
        assertEquals(new DecimalType(47), stateCaptor.getValue());

        heatingThing.handler.handleCommand(valueChannel.getUID(), new DecimalType(48));
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/heating.dhw.temperature.temp2/commands/setTargetTemperature"), Map.of("temperature", 48.0));
    }

    @Test
    public void bridgeAndDeviceHandleFeatureTimeout() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();


        Channel valueChannel = findChannelNoVerify(heatingThing.thingCaptor, "heating_dhw_temperature_temp2");
        when(vicareService.getFeatures(anyLong(), anyString(), anyString())).thenThrow(new IOException("Timed out"));

        heatingThing.handler.handleCommand(valueChannel.getUID(), RefreshType.REFRESH);

        var thingStatusInfoCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        InOrder inOrder = inOrder(heatingThing.bridgeCallback, heatingThing.callback);
        inOrder.verify(heatingThing.bridgeCallback, timeout(3000)).statusUpdated(eq(heatingThing.bridge), thingStatusInfoCaptor.capture());
        assertEquals(ThingStatus.OFFLINE, thingStatusInfoCaptor.getValue().getStatus());
        assertEquals(ThingStatusDetail.COMMUNICATION_ERROR, thingStatusInfoCaptor.getValue().getStatusDetail());
        inOrder.verify(heatingThing.callback, timeout(3000)).statusUpdated(eq(heatingThing.handler.getThing()), thingStatusInfoCaptor.capture());
        assertEquals(ThingStatus.OFFLINE, thingStatusInfoCaptor.getValue().getStatus());
        assertEquals(ThingStatusDetail.BRIDGE_OFFLINE, thingStatusInfoCaptor.getValue().getStatusDetail());
    }

    public static Stream<Arguments> source_ventilationOperatingModes() {
        return Stream.of(
                Arguments.of("ventilation_operating_modes_standby_active", "Standby Ventilation Mode Active", "Shows whether the Standby operating mode is active.", OnOffType.OFF),
                Arguments.of("ventilation_operating_modes_ventilation_active", "Ventilation Ventilation Mode Active", "Shows whether the Ventilation operating mode is active.", OnOffType.ON),
                Arguments.of("ventilation_operating_modes_standard_active", "Standard Ventilation Mode Active", "Shows whether the Standard operating mode is active.", OnOffType.OFF)
        );
    }

    @ParameterizedTest
    @MethodSource("source_ventilationOperatingModes")
    public void supportsVentilationOperatingModes(String channelId, String expectedLabel, String expectedDescription, State expectedValue) throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel channel = findChannelNoVerify(heatingThing.thingCaptor, channelId);
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        assertEquals(expectedLabel, channelType.getLabel());
        assertEquals(expectedDescription, channelType.getDescription());

        heatingThing.handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(channel.getUID()), stateCaptor.capture());
        assertEquals(expectedValue, stateCaptor.getValue());
    }

    @Test
    public void supportsVentilationOperatingModesActive() throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel valueChannel = findChannelNoVerify(heatingThing.thingCaptor, "ventilation_operating_modes_active_value");
        ChannelType valueChannelType = channelTypeRegistry.getChannelType(valueChannel.getChannelTypeUID());
        assertEquals("Active Ventilation Operating Mode", valueChannelType.getLabel());
        assertEquals("Shows current active operating mode enabled on the device. (read/write)", valueChannelType.getDescription());
        assertEquals(Set.of(new StateOption("standby", "standby"),
                             new StateOption("standard", "standard"),
                             new StateOption("ventilation", "ventilation")),
                     Set.copyOf(valueChannelType.getState().getOptions()));

        heatingThing.handler.handleCommand(valueChannel.getUID(), RefreshType.REFRESH);
        ArgumentCaptor<State> stateCaptor = forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(valueChannel.getUID()), stateCaptor.capture());
        assertEquals(new StringType("ventilation"), stateCaptor.getValue());
        heatingThing.handler.handleCommand(valueChannel.getUID(), new StringType("standby"));
        verify(vicareService, timeout(1000)).sendCommand(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/ventilation.operating.modes.active/commands/setMode"), Map.of("mode", "standby"));
    }

    public static Stream<Arguments> source_ventilationOperatingPrograms() {
        return Stream.of(
                Arguments.of("ventilation_operating_programs_levelOne_active", "Level One Ventilation Operating Program Active", "Shows whether the Level One program is active. (read-only)", OnOffType.OFF, null, null),
                Arguments.of("ventilation_operating_programs_levelTwo_active", "Level Two Ventilation Operating Program Active", "Shows whether the Level Two program is active. (read-only)", OnOffType.OFF, null, null),
                Arguments.of("ventilation_operating_programs_levelThree_active", "Level Three Ventilation Operating Program Active", "Shows whether the Level Three program is active. (read-only)", OnOffType.ON, null, null),
                Arguments.of("ventilation_operating_programs_levelFour_active", "Level Four Ventilation Operating Program Active", "Shows whether the Level Four program is active. (read-only)", OnOffType.OFF, null, null),
                Arguments.of("ventilation_operating_programs_comfort_active", "Comfort Ventilation Operating Program Active", "Shows whether the Comfort program is active. (read/write)", OnOffType.OFF, OnOffType.ON, URI.create("https://api.viessmann.com/iot/v1/equipment/installations/1234567/gateways/0123456789101112/devices/0/features/ventilation.operating.programs.comfort/commands/activate")),
                Arguments.of("ventilation_operating_programs_eco_active", "Eco Ventilation Operating Program Active", "Shows whether the Eco program is active. (read/write)", OnOffType.OFF, null, null),
                Arguments.of("ventilation_operating_programs_standby_active", "Standby Ventilation Operating Program Active", "Shows whether the Standby program is active. (read-only)", OnOffType.OFF, null, null)
                         );
    }

    @ParameterizedTest
    @MethodSource("source_ventilationOperatingPrograms")
    public void supportsVentilationOperatingPrograms(String channelName, String expectedLabel, String expectedDescription, State expectedState, Command newState, URI expectedURI) throws AuthenticationException, IOException, CommandFailureException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel valueChannel = findChannelNoVerify(heatingThing.thingCaptor, channelName);
        ChannelType valueChannelType = channelTypeRegistry.getChannelType(valueChannel.getChannelTypeUID());
        assertEquals(expectedLabel, valueChannelType.getLabel());
        assertEquals(expectedDescription, valueChannelType.getDescription());

        heatingThing.handler.handleCommand(valueChannel.getUID(), RefreshType.REFRESH);
        ArgumentCaptor<State> stateCaptor = ArgumentCaptor.forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(valueChannel.getUID()), stateCaptor.capture());
        assertEquals(expectedState, stateCaptor.getValue());

        if (newState != null) {
            heatingThing.handler.handleCommand(valueChannel.getUID(), newState);
            verify(vicareService, timeout(1000)).sendCommand(expectedURI, emptyMap());
        }
    }

    @Test
    public void supportsVentilationOperatingProgramsActive() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel valueChannel = findChannelNoVerify(heatingThing.thingCaptor, "ventilation_operating_programs_active_value");
        ChannelType valueChannelType = channelTypeRegistry.getChannelType(valueChannel.getChannelTypeUID());
        assertEquals("Active Ventilation Operating Program", valueChannelType.getLabel());
        assertEquals("Shows the current active operating program of the ventilation.", valueChannelType.getDescription());

        heatingThing.handler.handleCommand(valueChannel.getUID(), RefreshType.REFRESH);
        ArgumentCaptor<State> stateCaptor = ArgumentCaptor.forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(valueChannel.getUID()), stateCaptor.capture());
        assertEquals(new StringType("levelThree"), stateCaptor.getValue());
    }

    @Test
    public void supportsVentilationOperatingProgramsHoliday() throws AuthenticationException, IOException {
        HeatingThing heatingThing = initialiseHeatingThing();

        Channel activeChannel = findChannelNoVerify(heatingThing.thingCaptor, "ventilation_operating_programs_holiday_active");
        ChannelType activeChannelType = channelTypeRegistry.getChannelType(activeChannel.getChannelTypeUID());
        assertEquals("Ventilation Holiday Operating Program Active", activeChannelType.getLabel());
        assertEquals("Shows whether the holiday program is active.", activeChannelType.getDescription());

        heatingThing.handler.handleCommand(activeChannel.getUID(), RefreshType.REFRESH);
        ArgumentCaptor<State> stateCaptor = ArgumentCaptor.forClass(State.class);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(activeChannel.getUID()), stateCaptor.capture());
        assertEquals(OnOffType.OFF, stateCaptor.getValue());

        Channel startDateChannel = findChannelNoVerify(heatingThing.thingCaptor, "ventilation_operating_programs_holiday_start");
        ChannelType startChannelType = channelTypeRegistry.getChannelType(startDateChannel.getChannelTypeUID());
        assertEquals("Ventilation Holiday Operating Program Start", startChannelType.getLabel());
        heatingThing.handler.handleCommand(startDateChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(startDateChannel.getUID()), stateCaptor.capture());
        assertEquals(new DateTimeType(LocalDateTime.parse("2023-05-23T00:00:00").atZone(ZoneId.systemDefault())), stateCaptor.getValue());

        Channel endDateChannel = findChannelNoVerify(heatingThing.thingCaptor, "ventilation_operating_programs_holiday_end");
        ChannelType endChannelType = channelTypeRegistry.getChannelType(endDateChannel.getChannelTypeUID());
        assertEquals("Ventilation Holiday Operating Program End", endChannelType.getLabel());
        heatingThing.handler.handleCommand(endDateChannel.getUID(), RefreshType.REFRESH);
        verify(heatingThing.callback, timeout(1000)).stateUpdated(eq(endDateChannel.getUID()), stateCaptor.capture());
        assertEquals(new DateTimeType(LocalDate.parse("2023-05-30").atTime(LocalTime.MAX).atZone(ZoneId.systemDefault())), stateCaptor.getValue());

    }

    private HeatingThing initialiseDisconnectedHeatingThing() throws AuthenticationException, IOException {
        disconnectedHeatingInstallation();
        return postInitialiseHeatingThing();
    }

    private HeatingThing initialiseHeatingThing() throws AuthenticationException, IOException {
        simpleHeatingInstallation();
        return postInitialiseHeatingThing();
    }

    private HeatingThing postInitialiseHeatingThing() throws AuthenticationException, IOException {
        Bridge bridge = vicareBridge();
        ThingHandlerCallback bridgeCallback = createBridgeHandler(bridge);
        VicareHandlerFactory vicareHandlerFactory = new VicareHandlerFactory(bundleContext,
                                                                             vicareServiceProvider);
        Thing deviceThing = heatingDeviceThing(DEVICE_1_ID);
        ThingHandler handler = vicareHandlerFactory.createHandler(deviceThing);
        ThingHandlerCallback callback = simpleHandlerCallback(bridge, handler);
        registerAndInitialize(handler);
        InOrder inOrder = inOrder(vicareService, callback);
        inOrder.verify(vicareService, timeout(1000)).getFeatures(INSTALLATION_ID, GATEWAY_SERIAL, DEVICE_1_ID);
        ArgumentCaptor<Thing> thingCaptor = forClass(Thing.class);
        inOrder.verify(callback, timeout(1000).atLeastOnce()).statusUpdated(thingCaptor.capture(), any(ThingStatusInfo.class));

        HeatingThing result = new HeatingThing(bridge, handler, bridgeCallback, callback, inOrder, thingCaptor);
        return result;
    }

    private static class HeatingThing {
        public final ThingHandler handler;
        public final ThingHandlerCallback callback;
        public final ThingHandlerCallback bridgeCallback;
        public final InOrder inOrder;
        public final ArgumentCaptor<Thing> thingCaptor;
        public final Thing bridge;

        public HeatingThing(Bridge bridge, ThingHandler handler, ThingHandlerCallback bridgeHandlerCallback, ThingHandlerCallback callback, InOrder inOrder, ArgumentCaptor<Thing> thingCaptor) {
            this.bridge = bridge;
            this.handler = handler;
            this.bridgeCallback = bridgeHandlerCallback;
            this.callback = callback;
            this.inOrder = inOrder;
            this.thingCaptor = thingCaptor;
        }
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
        ChannelType channelType = xmlChannelTypeProvider.channelTypes.get(channel.getChannelTypeUID());
        assertNotNull(channelType, String.format("Channel type %s not in thing-types.xml", channel.getChannelTypeUID().getId()));
    }

}
