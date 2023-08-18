package com.qubular.openhab.binding.vicare.internal.channeltype;

import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeProvider;

public interface VicareChannelTypeProvider extends ChannelTypeProvider {
    void addChannelType(ChannelType channelType);
}
