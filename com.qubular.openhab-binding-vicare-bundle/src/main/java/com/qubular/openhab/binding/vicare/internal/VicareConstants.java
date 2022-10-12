package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.thing.ThingTypeUID;

public class VicareConstants {
    public static final String BINDING_ID = "vicare";
    public static final ThingTypeUID THING_TYPE_HEATING = new ThingTypeUID(BINDING_ID, "heating");
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");
    public static final String PROPERTY_DEVICE_UNIQUE_ID = "deviceUniqueId";

    public static final String PROPERTY_FEATURE_NAME = "featureName";

    public static final String PROPERTY_PROP_NAME = "propertyName";

    public static final String PROPERTY_COMMAND_NAME = "commandName";

    public static final String PROPERTY_PARAM_NAME = "paramName";
    public static final String PROPERTY_BINDING_VERSION = "bindingVersion";
}
