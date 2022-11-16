package com.qubular.openhab.binding.vicare.internal;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeUID;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

public class VicareChannelTypeProvider implements ChannelTypeProvider, ThingHandlerService {
    private VicareDeviceThingHandler thingHandler;

    @Override
    public void setThingHandler(ThingHandler thingHandler) {
        this.thingHandler = (VicareDeviceThingHandler) thingHandler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return thingHandler;
    }

    @Override
    public Collection<ChannelType> getChannelTypes(@Nullable Locale locale) {
        return thingHandler != null ? thingHandler.getChannelTypes(locale) : Collections.emptyList();
    }

    @Override
    public @Nullable ChannelType getChannelType(ChannelTypeUID channelTypeUID, @Nullable Locale locale) {
        return thingHandler != null ? thingHandler.getChannelType(channelTypeUID, locale) : null;
    }
}
