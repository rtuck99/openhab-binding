package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.features.NumericSensorFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FeatureUtil {
    private static final Pattern PATTERN_HEATING_CIRCUIT = Pattern.compile("heating\\.circuits\\.([\\d+])(\\..*)?");
    private static final Pattern PATTERN_HEATING_COMPRESSOR = Pattern.compile("heating\\.compressors\\.([\\d+])(\\..*)?");

    private static final Pattern PATTERN_ENERGY_SAVING_OPERATING_PROGRAM = Pattern.compile("(heating\\.circuits\\.[\\d+]\\.operating\\.programs\\.)(.+)EnergySaving");
    private static final Pattern PATTERN_DHW_OPERATING_MODE = Pattern.compile("(heating\\.dhw\\.operating\\.modes\\.)(.+)");

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

    static String templateId(Feature f, String propertyNameSuffix, final int truncation) {
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
                int featureTruncation = truncation;
                while (featureTruncation-- > 0) {
                    truncated = truncated.replaceAll("\\.[^.]+?$", ".-");
                }
                parentRoot = "template_" + truncated;
            }
        }
        parentRoot = parentRoot
                .replace(".", "_");
        return parentRoot + "_" + (truncation == 0 ? "-" : propertyNameSuffix);
    }

     private static String heatingCircuit(String featureName) {
        Matcher matcher = PATTERN_HEATING_CIRCUIT.matcher(featureName);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String heatingCompressor(String featureName) {
        Matcher matcher = PATTERN_HEATING_COMPRESSOR.matcher(featureName);
        return matcher.matches() ? matcher.group(1) : null;
    }

    static Map<String, String> extractTemplatePropertiesFromFeature(Feature feature, Map<String, String> props) {

        props.put("0", feature.getName().replaceAll(".*\\.([^.]*$)", "$1"));
        String heatingCircuit = FeatureUtil.heatingCircuit(feature.getName());
        if (heatingCircuit != null) {
            props.put("heatingCircuit", heatingCircuit);
        }
        String heatingCompressor = FeatureUtil.heatingCompressor(feature.getName());
        if (heatingCompressor != null) {
            props.put("heatingCompressor", heatingCompressor);
        }
        Matcher energySavingOPMatcher = PATTERN_ENERGY_SAVING_OPERATING_PROGRAM.matcher(feature.getName());
        if (energySavingOPMatcher.matches()) {
            props.put("operatingProgram", energySavingOPMatcher.group(2));
        }
        Matcher operatingModeMatcher = PATTERN_DHW_OPERATING_MODE.matcher(feature.getName());
        if (operatingModeMatcher.matches()) {
            props.put("operatingMode", operatingModeMatcher.group(2));
        }
        return props;
    }

    static List<CommandDescriptor> activateCommands(Feature f) {
        return f.getCommands().stream().filter(
                c -> c.getName().equals("activate") || c.getName().equals("deactivate")).collect(Collectors.toList());
    }
}
