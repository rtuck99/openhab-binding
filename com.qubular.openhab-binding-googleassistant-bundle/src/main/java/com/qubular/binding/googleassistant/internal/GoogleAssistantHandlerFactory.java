/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.qubular.binding.googleassistant.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantBindingConfig;
import com.qubular.binding.googleassistant.internal.grpc.GrpcGoogleAssistantService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;

import static com.qubular.binding.googleassistant.internal.GoogleAssistantBindingConstants.THING_TYPE_GOOGLEASSISTANT;

/**
 * The {@link GoogleAssistantHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.googleassistant", service = ThingHandlerFactory.class)
public class GoogleAssistantHandlerFactory extends BaseThingHandlerFactory {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_GOOGLEASSISTANT);
    private final Logger logger = LoggerFactory.getLogger(GoogleAssistantHandlerFactory.class);

    private final HttpClient secureClient;

    private final GoogleAssistantDynamicStateDescriptionProvider googleAssistantDynamicStateDescriptionProvider;
    private final OAuthService oAuthService;
    private final GoogleAssistantBindingConfig bindingConfig;
    private final GoogleAssistantAuthService googleAssistantAuthService;
    private final ThrottlingChannelExecutor throttlingChannelExecutor;

    @Activate
    public GoogleAssistantHandlerFactory(BundleContext bundleContext,
                                         @Reference HttpClientFactory httpClientFactory,
                                         @Reference GoogleAssistantDynamicStateDescriptionProvider googleAssistantDynamicStateDescriptionProvider,
                                         @Reference OAuthService oAuthService,
                                         @Reference GoogleAssistantBindingConfig bindingConfig,
                                         @Reference GoogleAssistantAuthService googleAssistantAuthService,
                                         @Reference ThrottlingChannelExecutor throttlingChannelExecutor) {
        logger.info("Starting Google Assistant Binding build {}", Instant.ofEpochMilli(bundleContext.getBundle().getLastModified()));
        this.throttlingChannelExecutor = throttlingChannelExecutor;
        this.secureClient = httpClientFactory.getCommonHttpClient();
        this.oAuthService = oAuthService;
        this.bindingConfig = bindingConfig;
        this.googleAssistantAuthService = googleAssistantAuthService;
        try {
            this.secureClient.start();
        } catch (Exception e) {
            // catching exception is necessary due to the signature of HttpClient.start()
            logger.warn("Failed to start insecure http client: {}", e.getMessage());
            throw new IllegalStateException("Could not create insecure HttpClient");
        }
        this.googleAssistantDynamicStateDescriptionProvider = googleAssistantDynamicStateDescriptionProvider;
        OAuthService.ClientCredentials clientCredentials = OAuthService.ClientCredentials.fromJson(bindingConfig.getClientCredentials());
        this.googleAssistantAuthService.setCredentials(clientCredentials);
    }

    @Deactivate
    public void deactivate() {
        logger.debug("Deactivating Google Assistant Handler Factory.");
        try {
            secureClient.stop();
        } catch (Exception e) {
            // catching exception is necessary due to the signature of HttpClient.stop()
            logger.warn("Failed to stop insecure http client: {}", e.getMessage());
        }
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        logger.debug("Creating Google Assistant Handler.");
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_GOOGLEASSISTANT.equals(thingTypeUID)) {
            OAuthService.OAuthSession oAuthSession;
            try {
                oAuthSession = oAuthService.maybeRefreshAccessToken(null)
                        .join();
            } catch (Exception e) {
                logger.warn("Unable to fetch OAuth session.", e);
                return null;
            }

            GoogleAssistantService googleAssistantService = new GrpcGoogleAssistantService(bindingConfig,
                    oAuthService,
                    oAuthSession);
            return new GoogleAssistantThingHandler(thing, googleAssistantDynamicStateDescriptionProvider, googleAssistantService, throttlingChannelExecutor);
        }

        return null;
    }
}
