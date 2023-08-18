package com.qubular.openhab.binding.vicare.internal.channeltype;

import com.qubular.openhab.binding.vicare.internal.channeltype.VicareChannelTypeProvider;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.osgi.service.component.annotations.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component(service = {ChannelTypeProvider.class, VicareChannelTypeProvider.class})
public class SimpleVicareChannelTypeProvider implements VicareChannelTypeProvider {
    private static final Map<ChannelTypeUID, ChannelType> channelTypes = new ConcurrentHashMap<>();

    @Override
    public Collection<ChannelType> getChannelTypes(@Nullable Locale locale) {
        return channelTypes.values();
    }

    @Override
    public @Nullable ChannelType getChannelType(ChannelTypeUID channelTypeUID, @Nullable Locale locale) {
        return channelTypes.get(channelTypeUID);
    }

    public void addChannelType(ChannelType channelType) {
        channelTypes.put(channelType.getUID(), channelType);
    }
}
