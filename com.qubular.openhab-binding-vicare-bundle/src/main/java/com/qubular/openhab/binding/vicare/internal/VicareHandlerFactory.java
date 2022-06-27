package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VicareHandlerFactory extends BaseThingHandlerFactory {
    private static final Logger logger = LoggerFactory.getLogger(VicareHandlerFactory.class);
    @Activate
    public VicareHandlerFactory() {
        logger.info("Activating Vicare Binding");
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating Vicare Binding");
    }

    @Override
    protected @org.eclipse.jdt.annotation.Nullable ThingHandler createHandler(Thing thing) {
        return null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return false;
    }
}
