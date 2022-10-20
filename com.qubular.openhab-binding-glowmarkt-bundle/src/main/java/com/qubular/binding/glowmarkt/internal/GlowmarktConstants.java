package com.qubular.binding.glowmarkt.internal;

import org.openhab.core.thing.ThingTypeUID;

public class GlowmarktConstants {
    public static final String BINDING_ID = "glowmarkt";

    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");

    public static final ThingTypeUID THING_TYPE_VIRTUAL_ENTITY = new ThingTypeUID(BINDING_ID, "virtualEntity");

    public static final String PROPERTY_BINDING_VERSION = "bindingVersion";
    public static final String PROPERTY_CLASSIFIER = "classifier";
    public static final String PROPERTY_PLAN_DETAIL_ID = "planDetailId";
    public static final String PROPERTY_RESOURCE_ID = "resourceId";
    public static final String PROPERTY_RESOURCE_NAME = "resourceName";
    public static final String PROPERTY_STRUCTURE_ID = "structureId";
    public static final String PROPERTY_TIER = "tier";
    public static final String PROPERTY_VIRTUAL_ENTITY_ID = "virtualEntityId";
    public static final String DEFAULT_CRON_SCHEDULE = "0 0 2 * * *";
}
