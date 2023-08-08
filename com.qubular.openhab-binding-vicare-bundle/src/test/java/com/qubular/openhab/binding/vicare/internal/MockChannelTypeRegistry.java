package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeRegistry;

class MockChannelTypeRegistry extends ChannelTypeRegistry {
    @Override
    protected void addChannelTypeProvider(ChannelTypeProvider channelTypeProviders) {
        super.addChannelTypeProvider(channelTypeProviders);
    }

    @Override
    protected void removeChannelTypeProvider(ChannelTypeProvider channelTypeProviders) {
        super.removeChannelTypeProvider(channelTypeProviders);
    }
}
