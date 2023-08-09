package com.qubular.openhab.binding.vicare.internal.channeltype;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.openhab.binding.vicare.internal.VicareChannelBuilder;
import com.qubular.vicare.model.Feature;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingRegistryChangeListener;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.PROPERTY_FEATURE_NAME;

@Component(immediate=true)
public class ChannelTypeManager {
    private static final int FEATURE_VALIDITY_SECS = 60;
    private static final Logger logger = LoggerFactory.getLogger(ChannelTypeManager.class);
    private final VicareServiceProvider vicareServiceProvider;
    private ThingRegistryChangeListener thingRegistryChangeListener;

    @Activate
    public ChannelTypeManager(@Reference VicareServiceProvider vicareServiceProvider) {
        logger.debug("Created ChannelTypeManager");
        this.vicareServiceProvider = vicareServiceProvider;

        ThingRegistry thingRegistry = vicareServiceProvider.getThingRegistry();
        thingRegistry.getAll().forEach(this::preloadChannelTypes);
        thingRegistryChangeListener = new ThingRegistryChangeListener() {
            @Override
            public void added(Thing thing) {
                logger.debug("Thing {} added", thing.getUID());
                preloadChannelTypes(thing);
            }

            @Override
            public void removed(Thing thing) {
                // don't care
            }

            @Override
            public void updated(Thing oldThing, Thing newThing) {
                logger.debug("Thing {} updated", newThing.getUID());
                preloadChannelTypes(newThing);
            }
        };
        thingRegistry.addRegistryChangeListener(thingRegistryChangeListener);
    }

    @SuppressWarnings("unused")
    @Deactivate
    public void deactivate() {
        logger.debug("Deactivating ChannelTypeManager");
        vicareServiceProvider.getThingRegistry().removeRegistryChangeListener(thingRegistryChangeListener);
    }

    private void preloadChannelTypes(Thing thing) {
        logger.debug("Preloading channels for {}", thing.getUID());
        thing.getChannels().forEach(channel -> {
            ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
            ChannelType channelType = vicareServiceProvider.getChannelTypeRegistry().getChannelType(channelTypeUID);
            if (channelType == null) {
                preloadChannelType(channel, channelTypeUID);
            }
        });
    }

    private void preloadChannelType(Channel channel, ChannelTypeUID channelTypeUID) {
        String featureName = channel.getProperties().get(PROPERTY_FEATURE_NAME);
        if (featureName != null) {
            Thing thing = vicareServiceProvider.getThingRegistry().get(channel.getUID().getThingUID());
            CompletableFuture<Optional<Feature>> featureFuture = vicareServiceProvider.getFeatureService()
                    .getFeature(thing, featureName, FEATURE_VALIDITY_SECS);
            VicareChannelBuilder vicareChannelBuilder = new VicareChannelBuilder(vicareServiceProvider, thing, ct -> {
                vicareServiceProvider.getChannelTypeProvider().addChannelType(ct);
            });
            featureFuture.thenAccept(feature -> feature.ifPresent(vicareChannelBuilder::buildChannelTypeForFeature));
        }
    }
}
