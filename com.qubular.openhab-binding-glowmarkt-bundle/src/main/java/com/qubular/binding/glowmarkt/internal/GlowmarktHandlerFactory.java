package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.GlowmarktService;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Set;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.THING_TYPE_BRIDGE;
import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.THING_TYPE_VIRTUAL_ENTITY;

@Component(service = ThingHandlerFactory.class)
public class GlowmarktHandlerFactory extends BaseThingHandlerFactory {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES =
            Set.of(THING_TYPE_BRIDGE, THING_TYPE_VIRTUAL_ENTITY);

    private final GlowmarktService glowmarktService;
    private final HttpClientFactory httpClientFactory;
    private final PersistenceServiceRegistry persistenceServiceRegistry;
    private final ItemChannelLinkRegistry itemChannelLinkRegistry;
    private CronScheduler cronScheduler;
    private final ConfigurationAdmin configurationAdmin;
    private final GlowmarktServiceProvider glowmarktServiceProvider;

    @Activate
    public GlowmarktHandlerFactory(@Reference GlowmarktService glowmarktService,
                                   @Reference GlowmarktServiceProvider serviceProvider,
                                   @Reference HttpClientFactory httpClientFactory,
                                   @Reference PersistenceServiceRegistry persistenceServiceRegistry,
                                   @Reference CronScheduler cronScheduler,
                                   @Reference ConfigurationAdmin configurationAdmin) {
        this.glowmarktServiceProvider = serviceProvider;
        this.glowmarktService = glowmarktService;
        this.httpClientFactory = httpClientFactory;
        this.persistenceServiceRegistry = persistenceServiceRegistry;
        this.itemChannelLinkRegistry = serviceProvider.getItemChannelLinkRegistry();
        this.cronScheduler = cronScheduler;
        this.configurationAdmin = configurationAdmin;
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if(THING_TYPE_BRIDGE.equals(thing.getThingTypeUID())) {
            return new GlowmarktBridgeHandler(glowmarktServiceProvider, (Bridge) thing, glowmarktService, httpClientFactory,
                                                persistenceServiceRegistry,
                                                cronScheduler, configurationAdmin);
        }
        if (THING_TYPE_VIRTUAL_ENTITY.equals(thing.getThingTypeUID())) {
            return new GlowmarktVirtualEntityHandler(glowmarktServiceProvider, thing, glowmarktService);
        }
        return null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }
}
