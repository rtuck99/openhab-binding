package com.qubular.binding.glowmarkt.internal;

import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingRegistryChangeListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.*;

@Component(immediate = true)
public class ChannelTypeManager {
    private static final Logger logger = LoggerFactory.getLogger(ChannelTypeManager.class);
    private final GlowmarktServiceProvider serviceProvider;

    @Activate
    public ChannelTypeManager(@Reference GlowmarktServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        ThingRegistry thingRegistry = serviceProvider.getThingRegistry();
        thingRegistry.getAll().forEach(this::preloadChannelTypes);
        thingRegistry.addRegistryChangeListener(new ThingRegistryChangeListener() {
            @Override
            public void added(Thing thing) {
                preloadChannelTypes(thing);
            }

            @Override
            public void removed(Thing thing) {

            }

            @Override
            public void updated(Thing oldThing, Thing newThing) {
                preloadChannelTypes(newThing);
            }
        });

    }

    private void preloadChannelTypes(Thing thing) {
        logger.debug("Preloading channels for {}", thing);
        thing.getChannels().forEach(channel -> preloadChannelType(thing, channel));
    }

    private void preloadChannelType(Thing thing, Channel channel) {
        Map<String, String> properties = channel.getProperties();
        String resourceName = properties.get(PROPERTY_RESOURCE_NAME);
        Integer tier = null;

        try {
            String tierString = properties.getOrDefault(PROPERTY_TIER, "");
            if (! tierString.isBlank()) {
                tier = Integer.parseInt(tierString);
            }
        } catch (NumberFormatException e) {
            // never mind
        }
        // creates and registers the channel type
        serviceProvider.getTariffChannelTypeProvider().createChannelType(
                channel.getChannelTypeUID(),
                Locale.getDefault(),
                resourceName,
                tier);

    }
}
