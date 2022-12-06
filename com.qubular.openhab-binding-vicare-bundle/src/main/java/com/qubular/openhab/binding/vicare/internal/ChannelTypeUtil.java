package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.ParamDescriptor;
import com.qubular.vicare.model.features.*;
import com.qubular.vicare.model.params.EnumParamDescriptor;
import com.qubular.vicare.model.params.NumericParamDescriptor;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.StateChannelTypeBuilder;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.openhab.core.types.StateOption;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class ChannelTypeUtil {
    static String substitutePropertyValues(String template, Map<String, String> props) {
        for (Map.Entry<String, String> e : props.entrySet()) {
            template = template.replaceAll("\\$\\{" + e.getKey() + "}", e.getValue());
            template = template.replaceAll("\\$\\{pretty:" + e.getKey() + "}", FeatureUtil.prettyFormat(e.getValue()));
        }
        return template;
    }

    static String deviceSpecificChannelTypeId(String channelId, Thing thing) {
        String prefix = thing.getUID().getAsString();
        byte[] md5s;
        try {
            md5s = MessageDigest.getInstance("MD5").digest(prefix.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to get MD5 message digest algorithm");
        }
        ByteBuffer encode = Base64.getEncoder()
                .withoutPadding()
                .encode(ByteBuffer.wrap(md5s).limit(8));
        return String.format("%s_%s", new String(encode.array(), StandardCharsets.UTF_8).replace('+','-').replace('/','_'), channelId);
    }

    static String channelIdToChannelType(String channelId) {
        return channelId.replaceAll("(_[\\d+])(_?)", "$2");
    }

    static StateChannelTypeBuilder stateDescription(Feature feature, StateChannelTypeBuilder builder, ChannelType template,
                                                    CommandDescriptor commandDescriptor) {
        StateDescription state = template.getState();
        if (state != null || commandDescriptor != null) {
            StateDescriptionFragmentBuilder sdf = StateDescriptionFragmentBuilder.create();
            ParamDescriptor paramDescriptor = commandDescriptor != null &&
                    commandDescriptor.getParams().size() == 1 ?
                    commandDescriptor.getParams().get(0) : null;
            feature.accept(new Feature.Visitor() {
                @Override
                public void visit(ConsumptionFeature f) {

                }

                @Override
                public void visit(NumericSensorFeature f) {
                    if (paramDescriptor == null || paramDescriptor instanceof NumericParamDescriptor) {
                        addNumericStateDescriptors(state, sdf, (NumericParamDescriptor) paramDescriptor);
                    }
                }

                @Override
                public void visit(StatusSensorFeature f) {
                }

                @Override
                public void visit(TextFeature f) {
                    if (paramDescriptor instanceof EnumParamDescriptor) {
                        sdf.withOptions(((EnumParamDescriptor)paramDescriptor).getAllowedValues()
                                .stream()
                                .map(s -> new StateOption(s, s))
                                .collect(Collectors.toList()));
                    }
                }

                @Override
                public void visit(CurveFeature f) {
                    if (paramDescriptor == null || paramDescriptor instanceof NumericParamDescriptor) {
                        addNumericStateDescriptors(state, sdf, (NumericParamDescriptor) paramDescriptor);
                    }
                }

                @Override
                public void visit(DatePeriodFeature datePeriodFeature) {

                }
            });

            if (state != null) {
                if (state.getPattern() != null)
                    sdf.withPattern(state.getPattern());
                if (state.getOptions() != null && !state.getOptions().isEmpty())
                    sdf.withOptions(state.getOptions());

                sdf.withReadOnly(state.isReadOnly());
            }

            return builder.withStateDescriptionFragment(sdf.build());
        } else {
            return builder;
        }
    }

    private static StateDescriptionFragmentBuilder addNumericStateDescriptors(StateDescription state,
                                                                              StateDescriptionFragmentBuilder sdf,
                                                                              NumericParamDescriptor paramDescriptor) {
        if (paramDescriptor != null && paramDescriptor.getMax() != null)
            sdf = sdf.withMaximum(BigDecimal.valueOf(paramDescriptor.getMax()));
        else if (state != null && state.getMaximum() != null)
            sdf = sdf.withMaximum(state.getMaximum());
        if (paramDescriptor != null && paramDescriptor.getMin() != null)
            sdf = sdf.withMinimum(BigDecimal.valueOf(paramDescriptor.getMin()));
        else if (state != null && state.getMinimum() != null)
            sdf = sdf.withMinimum(state.getMinimum());
        if (paramDescriptor != null && paramDescriptor.getStepping() != null)
            sdf = sdf.withStep(BigDecimal.valueOf(paramDescriptor.getStepping()));
        else if (state != null && state.getStep() != null)
            sdf = sdf.withStep(state.getStep());
        return sdf;
    }

    static Optional<CommandDescriptor> getSetterCommandDescriptor(Feature f, String propertyName) {
        Optional<CommandDescriptor> setter = f.getCommands().stream()
                .filter(cd -> cd.getName().equalsIgnoreCase("set" + propertyName))
                .findFirst();
        return setter;
    }
}
