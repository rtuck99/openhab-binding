package com.qubular.openhab.binding.vicare.internal.channeltype;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.UnitMapping;
import com.qubular.openhab.binding.vicare.internal.VicareUtil;
import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Unit;
import com.qubular.vicare.model.Value;
import com.qubular.vicare.model.features.*;
import com.qubular.vicare.model.values.*;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.thing.type.StateChannelTypeBuilder;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.*;
import static com.qubular.openhab.binding.vicare.internal.VicareDeviceThingHandler.COMMAND_NAMES_TO_PROPS;
import static com.qubular.openhab.binding.vicare.internal.VicareDeviceThingHandler.CONSUMPTION_CHANNEL_NAMES_BY_STAT;
import static com.qubular.openhab.binding.vicare.internal.VicareUtil.escapeUIDSegment;
import static java.lang.String.join;
import static java.util.Collections.*;

public class VicareChannelBuilder implements Supplier<VicareChannelBuilder.Memo> {
    public interface Memo {
        Result get() throws IOException, AuthenticationException;
    }

    public static class Result {
        public final List<Channel> channels = new ArrayList<>();
        public final Map<String, String> newPropValues;

        public Result(Map<String, String> newPropValues) {
            this.newPropValues = new HashMap<>(newPropValues);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(VicareChannelBuilder.class);
    private final VicareServiceProvider vicareServiceProvider;
    private final VicareUtil.IGD igd;
    private final Thing thing;
    private final BiFunction<ChannelUID, ChannelTypeUID, ChannelBuilder> channelBuilderFactory;
    private final Consumer<ChannelType> channelTypeListener;
    private boolean buildChannel = true;

    public VicareChannelBuilder(VicareServiceProvider vicareServiceProvider, Thing thing,
                                Consumer<ChannelType> channelTypeListener) {
        this(vicareServiceProvider, VicareUtil.decodeThingUniqueId(VicareUtil.getDeviceUniqueId(thing)),
             thing, null, channelTypeListener);
    }

    public VicareChannelBuilder(VicareServiceProvider vicareServiceProvider, VicareUtil.IGD igd, Thing thing,
                                BiFunction<ChannelUID, ChannelTypeUID, ChannelBuilder> channelBuilderFactory,
                                Consumer<ChannelType> channelTypeListener) {
        this.vicareServiceProvider = vicareServiceProvider;
        this.igd = igd;
        this.thing = thing;
        this.channelBuilderFactory = channelBuilderFactory != null ? channelBuilderFactory : (a, b) -> null;
        this.channelTypeListener = channelTypeListener != null ? channelTypeListener : (a) -> {};
    }

    @Override
    public Memo get() {
        Result result = new Result(thing.getProperties());
        try {
            List<Feature> features = vicareServiceProvider.getVicareService().getFeatures(igd.installationId(),
                                                                                          igd.gatewaySerial(),
                                                                                          igd.deviceId());
            for (Feature feature : features) {
                buildChannelsForFeature(feature, result);
            }
        } catch (AuthenticationException | IOException e) {
            return (() -> {
                throw e;
            });
        }
        return () -> result;
    }

    public void buildChannelTypeForFeature(Feature feature) {
        buildChannelsForFeature(feature, new Result(new HashMap<>()));
    }

    private void buildChannelsForFeature(Feature feature, Result result) {
        feature.accept(new FeatureVisitor(feature, result));
        result.channels.addAll(addChannelsForVoidParamCommands(feature));
    }

    private ChannelType findTemplate(Feature f, String propertyNameSuffix) {
        int truncation = -1;
        do {
            String templateId = FeatureUtil.templateId(f, propertyNameSuffix, truncation);
            if (templateId != null) {
                // Get the named template from the thing-types.xml
                logger.debug("Searching for " + templateId);
                ChannelType template = vicareServiceProvider.getChannelTypeRegistry().getChannelType(
                        new ChannelTypeUID(BINDING_ID, templateId));
                if (template != null) {
                    return template;
                }
            }
        } while (truncation++ < 1);

        // try to find a default fallback template
        Value value = f.getProperties().get(propertyNameSuffix);
        if (value != null) {
            var visitor = new Value.Visitor() {
                ChannelType template = null;

                @Override
                public void visit(ArrayValue arrayValue) {
                }

                @Override
                public void visit(BooleanValue booleanValue) {
                    template = vicareServiceProvider.getChannelTypeRegistry().getChannelType(new ChannelTypeUID(BINDING_ID, "template_boolean_value"));
                }

                @Override
                public void visit(DimensionalValue dimensionalValue) {
                    Unit unit = dimensionalValue.getUnit();
                    String templateId = VicareUtil.escapeUIDSegment(
                            "template_dimensional_value_" + (unit != null ? unit.getName() : "unitless"));
                    ChannelTypeUID templateUID = new ChannelTypeUID(BINDING_ID, templateId);
                    template = vicareServiceProvider.getChannelTypeRegistry().getChannelType(templateUID);
                    if (template == null) {
                        String itemType = "Number";
                        if (unit != null) {
                            itemType = UnitMapping.apiToItemType(unit);
                        }
                        template = ChannelTypeBuilder.state(templateUID, "${featureName} ${pretty:propertyName}",
                                                            itemType)
                                .withStateDescriptionFragment(StateDescriptionFragmentBuilder.create()
                                                                      .withReadOnly(true)
                                                                      .build())
                                .build();
                        vicareServiceProvider.getChannelTypeProvider().addChannelType(template);
                    }
                }

                @Override
                public void visit(LocalDateValue localDateValue) {
                }

                @Override
                public void visit(StatusValue statusValue) {
                    template = vicareServiceProvider.getChannelTypeRegistry().getChannelType(new ChannelTypeUID(BINDING_ID, "template_status_value"));
                }

                @Override
                public void visit(StringValue stringValue) {
                    template = vicareServiceProvider.getChannelTypeRegistry().getChannelType(new ChannelTypeUID(BINDING_ID, "template_string_value"));
                }
            };
            value.accept(visitor);
            return visitor.template;
        }
        return null;
    }

    private Optional<ChannelBuilder> channelBuilder(ChannelUID channelUID,
                                                    Feature feature,
                                                    String id,
                                                    ChannelType template,
                                                    Map<String, String> props) {
        return channelBuilder(channelUID, feature, id, template, props, emptyList());
    }

    private Optional<ChannelBuilder> channelBuilder(ChannelUID channelUID,
                                                    Feature feature,
                                                    String id,
                                                    ChannelType template,
                                                    Map<String, String> props,
                                                    CommandDescriptor commandDescriptor) {
        return channelBuilder(channelUID, feature, id, template, props,
                              commandDescriptor == null ? emptyList() : singletonList(commandDescriptor));
    }

    private Optional<ChannelBuilder> channelBuilder(ChannelUID channelUID,
                                                    Feature feature,
                                                    String id,
                                                    ChannelType template,
                                                    Map<String, String> props,
                                                    List<CommandDescriptor> commandDescriptors) {
        Optional<ChannelTypeUID> channelTypeUID = createChannelType(feature, template,
                                                                    id, props, commandDescriptors)
                .map(ChannelType::getUID);
        return buildChannel ? buildChannel(channelUID, channelTypeUID) : Optional.empty();
    }

    private Optional<ChannelBuilder> buildChannel(ChannelUID channelUID, Optional<ChannelTypeUID> channelTypeUID) {
        try {
            return channelTypeUID.map(ct -> channelBuilderFactory.apply(channelUID, ct));
        } catch (IllegalArgumentException e) {
            // Channel type not found
            logger.info("Unable to create channel {}: {}", channelUID, e.getMessage());
            return Optional.empty();
        }
    }


    private Collection<? extends Channel> addChannelsForVoidParamCommands(Feature feature) {
        return feature.getCommands().stream()
                .filter(cd -> cd.getParams().isEmpty())
                .map(cd -> {
                    String id = escapeUIDSegment(feature.getName() + "_" + cd.getName());

                    Optional<ChannelBuilder> channelBuilder = channelBuilder(new ChannelUID(thing.getUID(), id),
                                                                             feature,
                                                                             ChannelTypeUtil.channelIdToChannelType(id),
                                                                             null, emptyMap())
                            .map(cb -> cb.withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                                                PROPERTY_COMMAND_NAME, cd.getName())));
                    // required because JDK-8268312 ?
                    return channelBuilder
                            .map(ChannelBuilder::build);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<ChannelType> createChannelType(Feature feature, ChannelType template, String channelId,
                                                    Map<String, String> props, List<CommandDescriptor> commandDescriptors) {
        String candidateChannelTypeId = ChannelTypeUtil.channelIdToChannelType(channelId);
        ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, candidateChannelTypeId);
        ChannelType channelType = vicareServiceProvider.getChannelTypeRegistry().getChannelType(channelTypeUID);
        if (channelType != null) {
            logger.debug("Found channel type {} for channelId {}", channelTypeUID, channelId);
        } else {
            if (template == null) {
                logger.debug("No fallback template for feature {}, channelId {}", feature.getName(), channelId);
                return Optional.empty();
            } else {
                logger.debug("Created channel type for channelId {} from template {}", channelId, template.getUID());
            }
            String label = ChannelTypeUtil.substitutePropertyValues(template.getLabel(), props);


            channelTypeUID = new ChannelTypeUID(BINDING_ID, ChannelTypeUtil.deviceSpecificChannelTypeId(channelId,
                                                                                                        thing));
            StateChannelTypeBuilder builder = ChannelTypeBuilder.state(channelTypeUID, label, template.getItemType());
            List<String> description = new ArrayList<>();
            if (template.getDescription() != null) {
                description.add(ChannelTypeUtil.substitutePropertyValues(template.getDescription(), props));
            }
            builder.withCategory(template.getCategory())
                    .withAutoUpdatePolicy(template.getAutoUpdatePolicy());

            ChannelTypeUtil.stateDescription(feature, builder, template, commandDescriptors)
                    .ifPresent(frag -> {
                                   builder.withStateDescriptionFragment(frag);
                                   description.add(frag.isReadOnly() ? "(read-only)" : "(read/write)");
                               }
                    );
            if (!description.isEmpty()) {
                builder.withDescription(join(" ", description));
            }
            channelType = builder.build();
            channelTypeListener.accept(channelType);
        }
        return Optional.of(channelType);
    }

    private class FeatureVisitor implements Feature.Visitor {
        Result result;
        Feature feature;

        public FeatureVisitor(Feature feature, Result result) {
            this.feature = feature;
            this.result = result;
        }

        @Override
        public void visit(ConsumptionFeature f) {
            String id = escapeUIDSegment(f.getName());
            CONSUMPTION_CHANNEL_NAMES_BY_STAT.entrySet()
                    .stream()
                    .filter(e -> f.getConsumption(e.getKey()).isPresent())
                    .map(e -> consumptionChannel(id, f, e.getValue(), e.getKey()))
                    .forEach(c -> c.ifPresent(result.channels::add));
        }

        private Optional<Channel> consumptionChannel(String id, ConsumptionFeature feature, String statName, ConsumptionFeature.Stat stat) {
            Map<String, String> props = Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                               PROPERTY_PROP_NAME, statName);
            String itemType = feature.getConsumption(stat)
                    .map(DimensionalValue::getUnit)
                    .map(UnitMapping::apiToItemType)
                    .orElse("Number");

            return channelBuilder(new ChannelUID(thing.getUID(), id + "_" + statName),
                                  feature, id + "_" + statName, null, props)
                    .map(c -> c.withProperties(props).withAcceptedItemType(itemType))
                    .map(ChannelBuilder::build);
        }

        @Override
        public void visit(NumericSensorFeature f) {
            String id = escapeUIDSegment(f.getName());
            Map<String, String> props = new HashMap<>();
            props.put(PROPERTY_FEATURE_NAME, feature.getName());
            props.put(PROPERTY_PROP_NAME, f.getPropertyName());
            FeatureUtil.extractTemplatePropertiesFromFeature(f, props);
            maybeAddPropertiesForSetter(f, f.getPropertyName(), props);
            ChannelType mainTemplate = findTemplate(f, f.getPropertyName());
            channelBuilder(new ChannelUID(thing.getUID(), id), f, id, mainTemplate, props,
                           ChannelTypeUtil.getSetterCommandDescriptor(f, f.getPropertyName()).orElse(null))
                    .map(c -> c.withProperties(props))
                    .map(ChannelBuilder::build)
                    .ifPresent(result.channels::add);
            if (f.getStatus() != null && !StatusValue.NA.equals(f.getStatus())) {
                Map<String, String> statusProps = new HashMap<>();
                statusProps.put(PROPERTY_FEATURE_NAME, feature.getName());
                statusProps.put(PROPERTY_PROP_NAME, "status");
                FeatureUtil.extractTemplatePropertiesFromFeature(f, statusProps);
                String statusId = id + "_status";
                ChannelType statusTemplate = findTemplate(f, "status");
                channelBuilder(new ChannelUID(thing.getUID(), statusId), f, statusId, statusTemplate, statusProps)
                        .map(c -> c.withProperties(statusProps))
                        .map(ChannelBuilder::build)
                        .ifPresent(result.channels::add);
            } else if (f.isActive() != null) {
                Map<String, String> activeProps = new HashMap<>();
                String activeId = id + "_active";
                activeProps.put(PROPERTY_FEATURE_NAME, feature.getName());
                activeProps.put(PROPERTY_PROP_NAME, "active");
                FeatureUtil.extractTemplatePropertiesFromFeature(f, activeProps);
                ChannelType activeTemplate = findTemplate(f, "active");

                List<CommandDescriptor> commandDescriptors = FeatureUtil.activateCommands(f);
                commandDescriptors.forEach(
                        c -> activeProps.put(COMMAND_NAMES_TO_PROPS.get(c.getName()), c.getName()));
                channelBuilder(new ChannelUID(thing.getUID(), activeId), f, activeId, activeTemplate, activeProps,
                               commandDescriptors)
                        .map(c -> c.withProperties(activeProps))
                        .map(ChannelBuilder::build)
                        .ifPresent(result.channels::add);
            }
            addAdditionalNumericChannels(f);
        }

        private void addAdditionalNumericChannels(NumericSensorFeature f) {
            f.getProperties().forEach((propName, prop) -> {
                if (!List.of("active", "status", "value", f.getName()).contains(propName) &&
                        Value.TYPE_NUMBER.equals(prop.getType())) {
                    addChannelsForProperty(f, propName, prop);
                }
            });
        }

        private Optional<CommandDescriptor> maybeAddPropertiesForSetter(Feature f, String name, Map<String, String> props) {
            Optional<CommandDescriptor> setter = ChannelTypeUtil.getSetterCommandDescriptor(f, name);
            if (!setter.isPresent() && "value".equals(name)) {
                setter = f.getCommands().stream()
                        .filter(cd -> cd.getParams().size() == 1)
                        .findFirst();
            }
            if (setter.isPresent()) {
                CommandDescriptor command = setter.get();
                props.put(PROPERTY_COMMAND_NAME, command.getName());
                props.put(PROPERTY_PARAM_NAME, command.getParams().get(0).getName());
            }
            return setter;
        }

        @Override
        public void visit(StatusSensorFeature f) {
            f.getProperties().forEach((k, v) -> {
                addChannelsForProperty(f, k, v);
            });
        }

        private void addChannelsForProperty(Feature f, String propName, Value propValue) {
            switch (propName) {
                case "status":
                    String statusId = escapeUIDSegment(f.getName() + "_status");
                    HashMap<String, String> props = new HashMap<>();
                    props.put(PROPERTY_FEATURE_NAME, f.getName());
                    props.put(PROPERTY_PROP_NAME, "status");
                    channelBuilder(new ChannelUID(thing.getUID(), statusId), f, statusId,
                                   findTemplate(f, "status"),
                                   FeatureUtil.extractTemplatePropertiesFromFeature(f, props))
                            .map(cb -> cb.withProperties(props).build())
                            .ifPresent(result.channels::add);
                    break;
                case "active":
                    String id = escapeUIDSegment(f.getName() + "_" + propName);
                    List<CommandDescriptor> commandDescriptors = FeatureUtil.activateCommands(f);
                    Map<String, String> propMap = new HashMap<>(Map.of(PROPERTY_FEATURE_NAME, f.getName(),
                                                                       PROPERTY_PROP_NAME, propName));
                    commandDescriptors.forEach(
                            c -> propMap.put(COMMAND_NAMES_TO_PROPS.get(c.getName()), c.getName()));
                    channelBuilder(new ChannelUID(thing.getUID(), id), f, id, findTemplate(f, propName),
                                   FeatureUtil.extractTemplatePropertiesFromFeature(f, propMap),
                                   commandDescriptors)
                            .map(cb -> cb.withProperties(propMap).build())
                            .ifPresent(result.channels::add);
                    break;
                default:
                    var visitor = new Value.Visitor() {
                        @Override
                        public void visit(ArrayValue v) {
                            unsupportedValue(v);
                        }

                        @Override
                        public void visit(BooleanValue v) {
                            String id = escapeUIDSegment(f.getName() + "_" + propName);
                            HashMap<String, String> props = new HashMap<>();
                            props.put(PROPERTY_FEATURE_NAME, f.getName());
                            props.put(PROPERTY_PROP_NAME, propName);
                            FeatureUtil.extractTemplatePropertiesFromFeature(f, props);
                            channelBuilder(new ChannelUID(thing.getUID(), id), f, id,
                                           findTemplate(f, propName),
                                           props)
                                    .map(cb -> cb.withProperties(props).build())
                                    .ifPresent(result.channels::add);
                        }

                        @Override
                        public void visit(DimensionalValue v) {
                            Map<String, String> props = new HashMap<>();
                            props.put(PROPERTY_FEATURE_NAME, feature.getName());
                            props.put(PROPERTY_PROP_NAME, propName);
                            FeatureUtil.extractTemplatePropertiesFromFeature(f, props);
                            Optional<CommandDescriptor> commandDescriptor = maybeAddPropertiesForSetter(
                                    f, propName, props);
                            String id = escapeUIDSegment(f.getName() + "_" + propName);
                            channelBuilder(new ChannelUID(thing.getUID(), id), f, id,
                                           findTemplate(f, propName),
                                           props,
                                           commandDescriptor.orElse(null))
                                    .map(cb -> cb.withProperties(props).build())
                                    .ifPresent(result.channels::add);
                        }

                        @Override
                        public void visit(LocalDateValue v) {
                            unsupportedValue(v);
                        }

                        @Override
                        public void visit(StatusValue v) {
                            unsupportedValue(v);
                        }

                        @Override
                        public void visit(StringValue v) {
                            String id = escapeUIDSegment(f.getName() + "_" + propName);
                            HashMap<String, String> props = new HashMap<>();
                            props.put(PROPERTY_FEATURE_NAME, f.getName());
                            props.put(PROPERTY_PROP_NAME, propName);
                            FeatureUtil.extractTemplatePropertiesFromFeature(f, props);

                            channelBuilder(new ChannelUID(thing.getUID(), id), f, id,
                                           findTemplate(f, propName),
                                           props)
                                    .map(cb -> cb.withProperties(props).build())
                                    .ifPresent(result.channels::add);
                        }

                        private void unsupportedValue(Value v) {
                            logger.warn("Dropping unsupported value {} for {}.{}",
                                        v, f.getName(), propName);
                        }
                    };
                    propValue.accept(visitor);
            }
        }

        @Override
        public void visit(TextFeature f) {
            String propertyName = f.getProperties().keySet().stream().findFirst().get();
            String id = escapeUIDSegment(f.getName() + "_" + propertyName);
            ChannelUID channelUID = new ChannelUID(thing.getUID(), id);
            Map<String, String> props = new HashMap<>();
            props.put(PROPERTY_FEATURE_NAME, f.getName());
            props.put(PROPERTY_PROP_NAME, propertyName);
            CommandDescriptor command = null;
            if (f.getCommands().size() == 1 &&
                    f.getCommands().get(0).getParams().size() == 1) {
                command = feature.getCommands().get(0);
                props.put(PROPERTY_COMMAND_NAME, command.getName());
                props.put(PROPERTY_PARAM_NAME, command.getParams().get(0).getName());
            }
            channelBuilder(channelUID, f, id,
                           findTemplate(f, propertyName),
                           FeatureUtil.extractTemplatePropertiesFromFeature(f, props),
                           command)
                    .map(cb -> cb.withProperties(props)
                            .build())
                    .ifPresent(result.channels::add);
        }

        @Override
        public void visit(CurveFeature f) {
            String slopeId = escapeUIDSegment(f.getName() + "_slope");
            channelBuilder(new ChannelUID(thing.getUID(), slopeId), f, slopeId,
                           findTemplate(f, "slope"),
                           FeatureUtil.extractTemplatePropertiesFromFeature(f, new HashMap<>()))
                    .map(cb -> cb.withProperties(Map.of(PROPERTY_FEATURE_NAME, f.getName(),
                                                        PROPERTY_PROP_NAME, "slope")).build())
                    .ifPresent(result.channels::add);
            String shiftId = escapeUIDSegment(f.getName() + "_shift");
            channelBuilder(new ChannelUID(thing.getUID(), shiftId), f, shiftId,
                           findTemplate(f, "shift"),
                           FeatureUtil.extractTemplatePropertiesFromFeature(f, new HashMap<>()))
                    .map(cb -> cb.withProperties(Map.of(PROPERTY_FEATURE_NAME, f.getName(),
                                                        PROPERTY_PROP_NAME, "shift")).build())
                    .ifPresent(result.channels::add);
        }

        @Override
        public void visit(DatePeriodFeature datePeriodFeature) {
            String activeId = escapeUIDSegment(feature.getName() + "_active");
            String startId = escapeUIDSegment(feature.getName() + "_start");
            String endId = escapeUIDSegment(feature.getName() + "_end");
            channelBuilder(new ChannelUID(thing.getUID(), activeId), datePeriodFeature, activeId, null,
                           emptyMap())
                    .map(cb -> cb.withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                                        PROPERTY_PROP_NAME, "active")).build())
                    .ifPresent(result.channels::add);
            channelBuilder(new ChannelUID(thing.getUID(), startId), datePeriodFeature, startId, null,
                           emptyMap())
                    .map(cb -> cb.withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                                        PROPERTY_PROP_NAME, "start")).build())
                    .ifPresent(result.channels::add);
            channelBuilder(new ChannelUID(thing.getUID(), endId), datePeriodFeature, endId, null,
                           emptyMap())
                    .map(cb -> cb.withProperties(Map.of(PROPERTY_FEATURE_NAME, feature.getName(),
                                                        PROPERTY_PROP_NAME, "end")).build())
                    .ifPresent(result.channels::add);
        }
    }
}
