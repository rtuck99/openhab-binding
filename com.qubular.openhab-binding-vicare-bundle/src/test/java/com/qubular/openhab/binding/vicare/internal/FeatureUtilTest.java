package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.features.StatusSensorFeature;
import com.qubular.vicare.model.features.TextFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.*;

public class FeatureUtilTest {
    public static Stream<Arguments> camelCases() {
        return Stream.of(Arguments.of("normalHeating", "Normal Heating"),
                         Arguments.of("aBracadabra", "A Bracadabra"),
                         Arguments.of("reducedCooling", "Reduced Cooling"),
                         Arguments.of("oneTwoThree", "One Two Three"),
                         Arguments.of("a3Parter", "A 3 Parter"),
                         Arguments.of("the10Something", "The 10 Something"),
                         Arguments.of("the10thSomething", "The 10th Something"),
                         Arguments.of("a", "A"),
                         Arguments.of("", ""));
    }

    @ParameterizedTest
    @MethodSource("camelCases")
    public void prettyFormatGeneratesCorrectOutput(String input, String expected) {
        assertEquals(expected, FeatureUtil.prettyFormat(input));
    }

    public static Stream<Arguments> featureNames() {
        return Stream.of(
                Arguments.of("template_heating_circuits_operating_programs_energySaving_active", "heating.circuits.1.operating.programs.reducedEnergySaving", "active", 0),
                Arguments.of("template_heating_circuits_operating_programs_-_active", "heating.circuits.1.operating.programs.reduced", "active", 1),
                Arguments.of("template_heating_circuits_circulation_pump_status", "heating.circuits.0.circulation.pump", "status", 0),
                Arguments.of("template_heating_circuits_name", "heating.circuits.0", "name", 0),
                Arguments.of("template_heating_circuits_-_name", "heating.circuits.0.name", "name", 1)
        );
    }

    @MethodSource("featureNames")
    @ParameterizedTest
    public void templateIdGeneratesCorrectOutput(String expectedTemplateId, String featureName, String propertyName, int truncation) {
        assertEquals(expectedTemplateId,
                     FeatureUtil.templateId(new StatusSensorFeature(featureName, emptyMap()), propertyName, truncation));
    }

    public static Stream<Arguments> templateProperties() {
        return Stream.of(
                Arguments.of("heating.circuits.0.name", Map.of("0", "name",
                                                               "heatingCircuit", "0")),
                Arguments.of("heating.circuits.0", Map.of("0", "0",
                                                          "heatingCircuit", "0"))
        );
    }

    @MethodSource("templateProperties")
    @ParameterizedTest
    public void extractTemplatePropertiesFromFeature(String featureName, Map<String, String> expectedProperties) {
        Feature feature = new TextFeature(featureName, "name", "text");
        Map<String, String> propMap = FeatureUtil.extractTemplatePropertiesFromFeature(feature,
                                                                                               new HashMap<>());
        assertEquals(expectedProperties, propMap);
    }
}