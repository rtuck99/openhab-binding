package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.vicare.VicareService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component(service= ThingHandlerFactory.class)
public class VicareHandlerFactory extends BaseThingHandlerFactory {
    private static final Logger logger = LoggerFactory.getLogger(VicareHandlerFactory.class);
    private final VicareService vicareService;
    private final VicareServiceProvider vicareServiceProvider;

    @Activate
    public VicareHandlerFactory(BundleContext bundleContext,
                                @Reference VicareServiceProvider vicareServiceProvider) {
        this.vicareServiceProvider = vicareServiceProvider;
        Bundle bundle = bundleContext.getBundle();
        logger.info("Activating Vicare Binding build {}", Instant.ofEpochMilli(bundle.getLastModified()));
        this.vicareService = vicareServiceProvider.getVicareService();
    }

    @SuppressWarnings("unused")
    @Deactivate
    public void deactivate() {
        logger.info("Deactivating Vicare Binding");
    }

    @Override
    protected @org.eclipse.jdt.annotation.Nullable ThingHandler createHandler(Thing thing) {
        logger.info("Creating handler for {}", thing.getThingTypeUID());
        if (supportsThingType(thing.getThingTypeUID())) {
            if (VicareConstants.THING_TYPE_BRIDGE.equals(thing.getThingTypeUID())) {
                return new VicareBridgeHandler(vicareServiceProvider, (Bridge) thing);
            } else {
                return new VicareDeviceThingHandler(vicareServiceProvider, thing, vicareService);
            }
        }
        return null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return VicareConstants.BINDING_ID.equals(thingTypeUID.getBindingId());
    }
}
