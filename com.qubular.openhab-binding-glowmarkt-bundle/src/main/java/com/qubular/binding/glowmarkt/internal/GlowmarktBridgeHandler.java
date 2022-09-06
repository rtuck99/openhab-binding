package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.*;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;

public class GlowmarktBridgeHandler extends BaseBridgeHandler {
    private final GlowmarktService glowmarktService;
    private final HttpClientFactory httpClientFactory;
    private GlowmarktSession currentSession;
    private ScheduledFuture<?> resourceUpdateJob;

    public GlowmarktBridgeHandler(Bridge bridge, GlowmarktService glowmarktService, HttpClientFactory httpClientFactory) {
        super(bridge);
        this.glowmarktService = glowmarktService;
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        resourceUpdateJob = scheduler.scheduleAtFixedRate(resourceUpdateJob(), 0, 1, TimeUnit.DAYS);
    }

    @Override
    public void dispose() {
        if (resourceUpdateJob != null) {
            resourceUpdateJob.cancel(false);
            resourceUpdateJob = null;
        }
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    private Runnable resourceUpdateJob() {
        return () -> {
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                for (Channel channel : thing.getChannels()) {
                    handler.handleCommand(channel.getUID(), RefreshType.REFRESH);
                }
            }
        };
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(GlowmarktDiscoveryService.class);
    }

    GlowmarktService getGlowmarktService() {
        return glowmarktService;
    }

    GlowmarktSettings getGlowmarktSettings() {
        return new GlowmarktSettings() {
            @Override
            public String getApplicationId() {
                return ofNullable((String) getConfig().get("applicationId")).orElse(DEFAULT_APPLICATION_ID);
            }

            @Override
            public URI getApiEndpoint() {
                String serverUri = (String) getConfig().get("serverUri");
                return serverUri == null ? DEFAULT_URI_ENDPOINT : URI.create(serverUri);
            }

            @Override
            public HttpClient getHttpClient() {
                return httpClientFactory.getCommonHttpClient();
            }
        };
    }

    synchronized GlowmarktSession getGlowmarktSession() throws AuthenticationFailedException, IOException {
        if (currentSession == null ||
                Instant.now().plus(1, ChronoUnit.HOURS).isAfter(currentSession.getExpiry())) {
            String username = (String) getConfig().get("username");
            String password = (String) getConfig().get("password");
            currentSession = glowmarktService.authenticate(getGlowmarktSettings(), username, password);
        }
        return currentSession;
    }

    List<VirtualEntity> getVirtualEntities() throws IOException, AuthenticationFailedException {
        try {
            List<VirtualEntity> virtualEntities = getGlowmarktService().getVirtualEntities(getGlowmarktSession(), getGlowmarktSettings());
            updateStatus(ThingStatus.ONLINE);
            return virtualEntities;
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to fetch virtual entities: " + e.getMessage());
            throw e;
        } catch (AuthenticationFailedException e) {
            updateStatus(ThingStatus.UNINITIALIZED, ThingStatusDetail.CONFIGURATION_ERROR, "Unable to authenticate with Glowmarkt API: " + e.getMessage());
            throw e;
        }
    }
}
