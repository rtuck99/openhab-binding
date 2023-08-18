package com.qubular.openhab.binding.vicare.internal.thingtype;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.ThingTypeProvider;
import org.openhab.core.thing.type.ThingType;
import org.openhab.core.thing.type.ThingTypeBuilder;
import org.openhab.core.thing.type.ThingTypeRegistry;
import org.osgi.service.component.annotations.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_DEVICE_UNIQUE_ID;

@Component
public class VicareThingTypeProvider implements ThingTypeProvider {
    private final ConcurrentMap<ThingTypeUID, ThingType> thingTypes = new ConcurrentHashMap<>();

    @Override
    public Collection<ThingType> getThingTypes(@Nullable Locale locale) {
        return thingTypes.values();
    }

    @Override
    public @Nullable ThingType getThingType(ThingTypeUID thingTypeUID, @Nullable Locale locale) {
        return thingTypes.get(thingTypeUID);
    }

    public ThingType fetchOrCreateThingType(ThingTypeRegistry thingTypeRegistry,
                                            ThingTypeUID uid) {
        ThingType thingType = thingTypeRegistry.getThingType(uid, Locale.getDefault());
        if (thingType != null) {
            return thingType;
        }

        thingType = ThingTypeBuilder.instance(uid, String.format("Unknown %s thing", uid.getId()))
                .withRepresentationProperty(PROPERTY_DEVICE_UNIQUE_ID)
                .build();
        thingTypes.put(uid, thingType);
        return thingType;
    }
}
