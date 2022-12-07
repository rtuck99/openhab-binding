package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.model.Feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FeatureUtil {
    private static final Pattern PATTERN_HEATING_CIRCUIT = Pattern.compile("heating\\.circuits\\.([\\d+])(\\..*)?");

    private static final Pattern PATTERN_ENERGY_SAVING_OPERATING_PROGRAM = Pattern.compile("(heating\\.circuits\\.[\\d+]\\.operating\\.programs\\.)(.+)EnergySaving");

    private static final Pattern PATTERN_FORCED_LAST_FROM_SCHEDULE_OPERATING_PROGRAM = Pattern.compile("heating\\.circuits\\.[\\d+]\\.operating\\.programs\\.forcedLastFromSchedule");

    static String prettyFormat(String camelCase) {
        int wordStart = 0;
        List<String> words = new ArrayList<>();
        for (int i = 0; i < camelCase.length(); ++i) {
            if (i != wordStart &&
                    (Character.isUpperCase(camelCase.charAt(i)) ||
                    (Character.isDigit(camelCase.charAt(i)) && !Character.isDigit(camelCase.charAt(wordStart))))) {
                String word = Character.toUpperCase(camelCase.charAt(wordStart)) + camelCase.substring(wordStart + 1, i);
                words.add(word);
                wordStart = i;
            }
        }
        if (wordStart != camelCase.length()) {
            String word = Character.toUpperCase(camelCase.charAt(wordStart)) + camelCase.substring(wordStart + 1);
            words.add(word);
        }
        return words.stream().collect(Collectors.joining(" "));
    }

    static String templateId(Feature f, String propertyNameSuffix, int truncation) {
        Matcher energySavingOperatingProgramMatcher = PATTERN_ENERGY_SAVING_OPERATING_PROGRAM.matcher(f.getName());
        String parentRoot;
        if (energySavingOperatingProgramMatcher.matches()) {
            parentRoot = "template_" + energySavingOperatingProgramMatcher.group(1) + "energySaving";
            parentRoot = parentRoot.replaceAll("\\.\\d+", "");
        } else {
            Matcher forcedLastFromScheduleMatcher = PATTERN_FORCED_LAST_FROM_SCHEDULE_OPERATING_PROGRAM.matcher(f.getName());
            if (forcedLastFromScheduleMatcher.matches()) {
                parentRoot = "template_" + f.getName().replaceAll("\\.\\d+", "");
            } else {
                String truncated = f.getName().replaceAll("\\.\\d+", "");
                while (truncation-- > 0) {
                    truncated = truncated.replaceAll("\\.[^.]+?$", ".-");
                }
                parentRoot = "template_" + truncated;
            }
        }
        parentRoot = parentRoot
                .replace(".", "_");
        return parentRoot + "_" + propertyNameSuffix;
    }

    static String heatingCircuit(String featureName) {
        Matcher matcher = PATTERN_HEATING_CIRCUIT.matcher(featureName);
        return matcher.matches() ? matcher.group(1) : "?";
    }

    static Map<String, String> extractTemplatePropertiesFromFeature(Feature feature, Map<String, String> props) {

        props.put("0", feature.getName().replaceAll(".*\\.([^.]*$)", "$1"));
        props.put("heatingCircuit", FeatureUtil.heatingCircuit(feature.getName()));
        Matcher energySavingOPMatcher = PATTERN_ENERGY_SAVING_OPERATING_PROGRAM.matcher(feature.getName());
        if (energySavingOPMatcher.matches()) {
            props.put("operatingProgram", energySavingOPMatcher.group(2));
        }
        return props;
    }
}
