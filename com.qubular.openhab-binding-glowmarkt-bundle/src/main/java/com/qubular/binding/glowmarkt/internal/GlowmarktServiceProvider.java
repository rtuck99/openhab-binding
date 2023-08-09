package com.qubular.binding.glowmarkt.internal;

import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeRegistry;

public interface GlowmarktServiceProvider {
    ThingRegistry getThingRegistry();
    ChannelTypeRegistry getChannelTypeRegistry();
    ItemChannelLinkRegistry getItemChannelLinkRegistry();
    TariffChannelTypeProvider getTariffChannelTypeProvider();
    String getBindingVersion();
}
