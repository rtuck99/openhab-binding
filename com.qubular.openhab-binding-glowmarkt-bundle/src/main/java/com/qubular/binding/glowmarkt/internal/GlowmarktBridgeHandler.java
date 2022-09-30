package com.qubular.binding.glowmarkt.internal;

import com.qubular.glowmarkt.*;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.persistence.ModifiablePersistenceService;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.scheduler.ScheduledCompletableFuture;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;

public class GlowmarktBridgeHandler extends BaseBridgeHandler {
    private static final String PID = "com.qubular.binding.glowmarkt.GlowmarktBridgeHandler";
    public static final String CONFIG_PARAM_APPLICATION_ID = "applicationId";
    public static final String CONFIG_PARAM_SERVER_URI = "serverUri";

    public static final String CONFIG_PARAM_PERSISTENCE_SERVICE = "persistenceService";
    public static final String CONFIG_PARAM_USERNAME = "username";
    public static final String CONFIG_PARAM_PASSWORD = "password";
    private static final String CONFIG_PARAM_SECURE_PASSWORD = "securePassword";
    public static final String CONFIG_PARAM_CRON_SCHEDULE = "cronSchedule";

    private static final Logger logger = LoggerFactory.getLogger(GlowmarktBridgeHandler.class);

    private final GlowmarktService glowmarktService;
    private final HttpClientFactory httpClientFactory;
    private final PersistenceServiceRegistry persistenceServiceRegistry;
    private final CronScheduler cronScheduler;
    private GlowmarktSession currentSession;
    private ScheduledFuture<?> oneTimeUpdateJob;
    private ScheduledCompletableFuture<Void> cronUpdateJob;

    private CryptUtil cryptUtil;

    public GlowmarktBridgeHandler(Bridge bridge,
                                  GlowmarktService glowmarktService,
                                  HttpClientFactory httpClientFactory,
                                  PersistenceServiceRegistry persistenceServiceRegistry,
                                  CronScheduler cronScheduler,
                                  @Reference ConfigurationAdmin configurationAdmin) {
        super(bridge);
        this.glowmarktService = glowmarktService;
        this.httpClientFactory = httpClientFactory;
        this.persistenceServiceRegistry = persistenceServiceRegistry;
        this.cronScheduler = cronScheduler;
        try {
            this.cryptUtil = new CryptUtil(configurationAdmin.getConfiguration(PID));
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize configuration", e);
        }
    }

    @Override
    public void initialize() {
        logger.trace("Initializing GlowmarktBridgeHandler");
        migratePassword();
        updateStatus(ThingStatus.UNKNOWN);
        oneTimeUpdateJob = scheduler.schedule(resourceUpdateJob(),5, TimeUnit.SECONDS);
        cronUpdateJob = cronScheduler.schedule(() -> resourceUpdateJob().run(), getCronSchedule());
    }

    private void migratePassword() {
        Configuration config = getConfig();
        String password = (String) config.get(CONFIG_PARAM_PASSWORD);
        if (password != null && !password.replaceAll("\\*", "").isEmpty()) {
            logger.info("Encrypting password");
            try {
                Configuration editable = editConfiguration();
                String encrypted = cryptUtil.encrypt(password);
                editable.put(CONFIG_PARAM_SECURE_PASSWORD, encrypted);
                editable.put(CONFIG_PARAM_PASSWORD, password.replaceAll(".", "*"));
                updateConfiguration(editable);
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException("Unable to encrypt password.", e);
            }
        }
    }

    @Override
    public void dispose() {
        logger.trace("Disposing GlowmarktBridgeHandler");
        if (oneTimeUpdateJob != null) {
            oneTimeUpdateJob.cancel(false);
            oneTimeUpdateJob = null;
        }
        if (cronUpdateJob != null) {
            cronUpdateJob.cancel(false);
            cronUpdateJob = null;
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
                return ofNullable((String) getConfig().get(CONFIG_PARAM_APPLICATION_ID)).orElse(DEFAULT_APPLICATION_ID);
            }

            @Override
            public URI getApiEndpoint() {
                String serverUri = (String) getConfig().get(CONFIG_PARAM_SERVER_URI);
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
            String username = (String) getConfig().get(CONFIG_PARAM_USERNAME);
            String password = null;
            try {
                password = cryptUtil.decrypt((String) getConfig().get(CONFIG_PARAM_SECURE_PASSWORD));
            } catch (GeneralSecurityException e) {
                throw new RuntimeException("Unable to decrypt password.", e);
            }
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

    ModifiablePersistenceService getPersistenceService() {
        return (ModifiablePersistenceService) persistenceServiceRegistry.get((String) getConfig().get(CONFIG_PARAM_PERSISTENCE_SERVICE));
    }

    private String getCronSchedule() {
        return (String) ofNullable(getConfig().get(CONFIG_PARAM_CRON_SCHEDULE)).orElse(GlowmarktConstants.DEFAULT_CRON_SCHEDULE);
    }
}
