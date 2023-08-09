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
import java.math.BigDecimal;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.qubular.binding.glowmarkt.internal.GlowmarktConstants.PROPERTY_BINDING_VERSION;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
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
    public static final String CONFIG_USE_LIMITED_ENCRYPTION = "useLimitedEncryption";
    public static final String CONFIG_PARAM_MAX_PAST_YEARS_TO_FETCH = "maxPastYearsToFetch";

    private static final Logger logger = LoggerFactory.getLogger(GlowmarktBridgeHandler.class);

    private final GlowmarktServiceProvider serviceProvider;
    private final GlowmarktService glowmarktService;
    private final HttpClientFactory httpClientFactory;
    private final PersistenceServiceRegistry persistenceServiceRegistry;
    private final CronScheduler cronScheduler;
    private GlowmarktSession currentSession;
    private ScheduledFuture<?> oneTimeUpdateJob;
    private ScheduledCompletableFuture<Void> cronUpdateJob;

    private CryptUtil cryptUtil;

    public GlowmarktBridgeHandler(GlowmarktServiceProvider serviceProvider, Bridge bridge,
                                  GlowmarktService glowmarktService,
                                  HttpClientFactory httpClientFactory,
                                  PersistenceServiceRegistry persistenceServiceRegistry,
                                  CronScheduler cronScheduler,
                                  @Reference ConfigurationAdmin configurationAdmin) {
        super(bridge);
        this.serviceProvider = serviceProvider;
        this.glowmarktService = glowmarktService;
        this.httpClientFactory = httpClientFactory;
        this.persistenceServiceRegistry = persistenceServiceRegistry;
        this.cronScheduler = cronScheduler;
        try {
            org.osgi.service.cm.Configuration configuration = applyConfiguration(configurationAdmin);
            this.cryptUtil = new CryptUtil(configuration);
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize configuration", e);
        }
    }

    private org.osgi.service.cm.Configuration applyConfiguration(ConfigurationAdmin configurationAdmin) throws IOException {
        Map<String, Object> configProps = getConfig().getProperties();
        boolean useLimitedEncryption = requireNonNullElse((Boolean) configProps.get(CONFIG_USE_LIMITED_ENCRYPTION), false);
        org.osgi.service.cm.Configuration configuration = configurationAdmin.getConfiguration(PID);
        Dictionary<String, Object> configAdminProps = requireNonNullElseGet(configuration.getProperties(), Hashtable::new);
        configAdminProps.put(CryptUtil.CONFIG_USE_LIMITED_ENCRYPTION, useLimitedEncryption);
        configuration.update(configAdminProps);
        return configuration;
    }

    @Override
    public void initialize() {
        logger.info("Initializing GlowmarktBridgeHandler");
        if (migratePassword()) {
            updateStatus(ThingStatus.UNKNOWN);
        }
        updateProperty(PROPERTY_BINDING_VERSION, serviceProvider.getBindingVersion());
        oneTimeUpdateJob = scheduler.schedule(resourceUpdateJob(),5, TimeUnit.SECONDS);
        cronUpdateJob = cronScheduler.schedule(() -> resourceUpdateJob().run(), getCronSchedule());
    }

    private boolean migratePassword() {
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
            } catch (InvalidKeyException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                             "Unable to encrypt your password, please check whether your crypto.policy is set" +
                                     " to enable full strength encryption or enable limited encryption in Advanced" +
                                     " Settings.");
                return false;
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException("Unable to encrypt password.", e);
            }
        }
        return true;
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
                throw new AuthenticationFailedException("Unable to decrypt password: " + e.getMessage(), e);
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

    int getMaxPastYearsToFetch() {
        return ofNullable(getConfig().get(CONFIG_PARAM_MAX_PAST_YEARS_TO_FETCH))
                .map(BigDecimal.class::cast)
                .map(BigDecimal::intValue)
                .map(i -> i <= 0 ? 99 : i)
                .orElse(GlowmarktConstants.DEFAULT_MAX_PAST_YEARS);
    }

}
