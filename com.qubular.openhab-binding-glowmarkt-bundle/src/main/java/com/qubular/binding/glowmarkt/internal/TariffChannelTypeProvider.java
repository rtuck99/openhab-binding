package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.Resource;
import com.qubular.glowmarkt.TariffPlanDetail;
import com.qubular.glowmarkt.TariffStructure;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.type.*;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.BINDING_ID;

@Component
public class TariffChannelTypeProvider implements ChannelTypeProvider {
    private static final ChannelTypeUID TARIFF_STANDING_CHARGE = new ChannelTypeUID(BINDING_ID, "tariff_standing_charge");
    private static final ChannelTypeUID TARIFF_PER_UNIT_RATE = new ChannelTypeUID(BINDING_ID, "tariff_per_unit_rate");
    public static final String PREFIX_TARIFF_STANDING_CHARGE = "tariff_standing_charge_";
    public static final String PREFIX_TARIFF_PER_UNIT_RATE = "tariff_per_unit_rate_";
    private static final Set<String> MANAGED_CHANNEL_PREFIXES = Set.of(PREFIX_TARIFF_STANDING_CHARGE,
                                                                       PREFIX_TARIFF_PER_UNIT_RATE);

    private ChannelTypeRegistry channelTypeRegistry;

    private final ConcurrentMap<ChannelTypeUID, ChannelType> channelTypes = new ConcurrentHashMap<>();

    @Activate
    public TariffChannelTypeProvider(@Reference ChannelTypeRegistry channelTypeRegistry) {
        this.channelTypeRegistry = channelTypeRegistry;
    }

    @Override
    public Collection<ChannelType> getChannelTypes(@Nullable Locale locale) {
        return channelTypes.values();
    }

    @Override
    public @Nullable ChannelType getChannelType(ChannelTypeUID channelTypeUID, @Nullable Locale locale) {
        return channelTypes.get(channelTypeUID);
    }

    public @Nullable ChannelType createChannelType(ChannelTypeUID channelTypeUID, @Nullable Locale locale, String resourceName, Integer tier) {
        return channelTypes.computeIfAbsent(channelTypeUID, channelTypeUID1 -> createChannelType(channelTypeUID1, resourceName, tier == null ? null : String.valueOf(tier)));
    }

    static boolean isManagedChannelType(ChannelTypeUID channelTypeUID) {
        return BINDING_ID.equals(channelTypeUID.getBindingId()) &&
                MANAGED_CHANNEL_PREFIXES.stream().anyMatch(channelTypeUID.getId()::startsWith);
    }

    private ChannelType createChannelType(ChannelTypeUID channelTypeUID, String resourceName, String tier) {
        if (channelTypeUID.getId().startsWith(PREFIX_TARIFF_PER_UNIT_RATE)) {
            return createChannelTypeFromTemplate(channelTypeUID, TARIFF_PER_UNIT_RATE, resourceName, tier);
        } else if (channelTypeUID.getId().startsWith(PREFIX_TARIFF_STANDING_CHARGE)) {
            return createChannelTypeFromTemplate(channelTypeUID, TARIFF_STANDING_CHARGE, resourceName, tier);
        } else {
            throw new IllegalStateException("Unsupported channel type " + channelTypeUID);
        }
    }

    private ChannelType createChannelTypeFromTemplate(ChannelTypeUID channelTypeUID, ChannelTypeUID templateChannelTypeUID, String resourceName, String tier) {
        ChannelType template = channelTypeRegistry.getChannelType(templateChannelTypeUID);
        String label = template.getLabel();
        if (resourceName != null) {
                label = label.replaceAll("\\$\\{resourceName}", resourceName);
        }
        if (tier == null) {
            tier = "";
        } else {
            tier = "Tier " + tier;
        }
        label = label.replaceAll("\\$\\{tier}", tier);
        StateChannelTypeBuilder stateChannelTypeBuilder = ChannelTypeBuilder.state(channelTypeUID, label,
                                                                                   template.getItemType())
                .withDescription(template.getDescription());
        if (template.getState() != null) {
            stateChannelTypeBuilder.withStateDescriptionFragment(StateDescriptionFragmentBuilder.create(
                                                                 template.getState()).build());
        }
        return stateChannelTypeBuilder.build();
    }

    public static String channelId(String prefix, Resource resource, TariffStructure tariffStructure, TariffPlanDetail planDetail) {
        return String.format("%s%s_%s_%s", prefix, resource.getResourceId(), tariffStructure.getId(), planDetail.getId())
                .replaceAll("\\s", "_");
    }
}
