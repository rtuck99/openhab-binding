package com.qubular.binding.glowmarkt.internal;

import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeRegistry;

public interface GlowmarktServiceProvider {
    ChannelTypeRegistry getChannelTypeRegistry();
    ItemChannelLinkRegistry getItemChannelLinkRegistry();

    String getBindingVersion();
}
