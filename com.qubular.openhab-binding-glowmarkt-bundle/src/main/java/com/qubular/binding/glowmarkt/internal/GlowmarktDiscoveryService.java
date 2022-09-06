package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.AuthenticationFailedException;
import com.qubular.glowmarkt.GlowmarktSession;
import com.qubular.glowmarkt.VirtualEntity;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.PROPERTY_VIRTUAL_ENTITY_ID;
import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.THING_TYPE_VIRTUAL_ENTITY;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.HOURS;

public class GlowmarktDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {
    private static final Logger logger = LoggerFactory.getLogger(GlowmarktDiscoveryService.class);
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = emptySet();
    
    private static final int DISCOVERY_TIMEOUT_SECS = 10;

    private ScheduledFuture<?> backgroundJob;

    private CompletableFuture<GlowmarktBridgeHandler> bridgeHandler = new CompletableFuture<>();
    
    public GlowmarktDiscoveryService() {
        super(SUPPORTED_THING_TYPES, DISCOVERY_TIMEOUT_SECS, true);
        activate(emptyMap());
    }

    @Override
    protected synchronized void startBackgroundDiscovery() {
        if (backgroundJob == null) {
            backgroundJob = scheduler.scheduleAtFixedRate(scanJob(), 0, 1, HOURS);
        }
    }

    @Override
    public void startScan() {
        scheduler.submit(scanJob());
    }

    @Override
    protected synchronized void stopBackgroundDiscovery() {
        if (backgroundJob != null) {
            backgroundJob.cancel(false);
            backgroundJob = null;
        }
    }

    private Runnable scanJob() {
        return () -> {
            try {
                List<VirtualEntity> virtualEntities = getThingHandler().getVirtualEntities();
                virtualEntities.forEach(ve -> {
                    String virtualEntityId = ve.getVirtualEntityId();
                    ThingUID bridgeUid = getThingHandler().getThing().getUID();
                    ThingUID thingUID = new ThingUID(THING_TYPE_VIRTUAL_ENTITY,
                            bridgeUid,
                            virtualEntityId);
                    DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                            .withBridge(bridgeUid)
                            .withProperty(PROPERTY_VIRTUAL_ENTITY_ID, virtualEntityId)
                            .withRepresentationProperty(PROPERTY_VIRTUAL_ENTITY_ID)
                            .build();
                    logger.debug("Discovered virtual entity {}", virtualEntityId);
                    thingDiscovered(result);
                });
            } catch (IOException e) {
                logger.debug("Unable to fetch virtual entities: " + e.getMessage(), e);
            } catch (AuthenticationFailedException e) {
                logger.debug("Unable to authenticate with Glowmarkt API: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        bridgeHandler.complete((GlowmarktBridgeHandler) handler);
    }

    @Override
    public @Nullable GlowmarktBridgeHandler getThingHandler() {
        return bridgeHandler.join();
    }

    @Override
    public void deactivate() {
        ThingHandlerService.super.deactivate();
        stopBackgroundDiscovery();
    }
}
