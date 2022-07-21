package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.VicareService;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;
import java.util.Set;

import static com.qubular.openhab.binding.vicare.internal.VicareConstants.THING_TYPE_BRIDGE;

public class VicareHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES =
            Set.of(VicareConstants.THING_TYPE_BRIDGE, VicareConstants.THING_TYPE_HEATING);
    private static final Logger logger = LoggerFactory.getLogger(VicareHandlerFactory.class);
    private final ThingRegistry thingRegistry;
    private final VicareService vicareService;

    @Activate
    public VicareHandlerFactory(@Reference ThingRegistry thingRegistry,
                                @Reference VicareService vicareService) {
        logger.info("Activating Vicare Binding");
        this.thingRegistry = thingRegistry;
        this.vicareService = vicareService;
        thingRegistry.addRegistryChangeListener(thingRegistryChangeListener);
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating Vicare Binding");
        thingRegistry.removeRegistryChangeListener(thingRegistryChangeListener);
    }

    @Override
    protected @org.eclipse.jdt.annotation.Nullable ThingHandler createHandler(Thing thing) {
        if (VicareConstants.THING_TYPE_BRIDGE.equals(thing.getThingTypeUID())) {
            return new VicareBridgeHandler(vicareService, thingRegistry, (Bridge) thing);
        } else if (VicareConstants.THING_TYPE_HEATING.equals(thing.getThingTypeUID())) {
            return new VicareDeviceThingHandler(thing, vicareService);
        }
        return null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }
    private final ThingRegistryChangeListener thingRegistryChangeListener = new ThingRegistryChangeListener() {
        @Override
        public void added(Thing element) {
            if (THING_TYPE_BRIDGE.equals(element.getThingTypeUID())) {
                logger.info("Viessmann API Bridge created");
                VicareDiscoveryService discoveryService = new VicareDiscoveryService(vicareService, element.getUID());
                bundleContext.registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<>());
                discoveryService.startScan();
            }
        }

        @Override
        public void removed(Thing element) {
            if (THING_TYPE_BRIDGE.equals(element.getThingTypeUID())) {
                logger.info("Viessmann API Bridge removed");
            }
        }

        @Override
        public void updated(Thing oldElement, Thing element) {

        }
    };
}
