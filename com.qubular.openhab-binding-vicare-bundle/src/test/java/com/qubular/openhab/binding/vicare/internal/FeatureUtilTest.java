package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.model.features.StatusSensorFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
                Arguments.of("template_heating_circuits_operating_programs_energySaving_active", "heating.circuits.1.operating.programs.reducedEnergySaving", "active"),
                Arguments.of("template_heating_circuits_operating_programs_active", "heating.circuits.1.operating.programs.reduced", "active")
        );
    }

    @MethodSource("featureNames")
    @ParameterizedTest
    public void templateIdGeneratesCorrectOutput(String expectedTemplateId, String featureName, String propertyName) {
        assertEquals(expectedTemplateId,
                     FeatureUtil.templateId(new StatusSensorFeature(featureName, emptyMap()), propertyName));
    }
}