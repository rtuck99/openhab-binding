package com.qubular.binding.glowmarkt.internal;

import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = GlowmarktServiceProvider.class)
public class BundleServiceProvider implements GlowmarktServiceProvider {

    @Reference
    private ChannelTypeRegistry channelTypeRegistry;
    @Reference
    private ItemChannelLinkRegistry itemChannelLinkRegistry;
    @Reference(target = "(component.name=com.qubular.binding.glowmarkt.internal.TariffChannelTypeProvider)")
    private ChannelTypeProvider tariffChannelTypeProvider;
    @Reference
    private ThingRegistry thingRegistry;
    private BundleContext bundleContext;

    @Activate
    public BundleServiceProvider(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public ChannelTypeRegistry getChannelTypeRegistry() {
        return channelTypeRegistry;
    }

    @Override
    public ItemChannelLinkRegistry getItemChannelLinkRegistry() {
        return itemChannelLinkRegistry;
    }

    @Override
    public TariffChannelTypeProvider getTariffChannelTypeProvider() {
        return (TariffChannelTypeProvider) tariffChannelTypeProvider;
    }

    @Override
    public ThingRegistry getThingRegistry() {
        return thingRegistry;
    }

    @Override
    public String getBindingVersion() {
        return bundleContext.getBundle().getVersion().toString();
    }
}
