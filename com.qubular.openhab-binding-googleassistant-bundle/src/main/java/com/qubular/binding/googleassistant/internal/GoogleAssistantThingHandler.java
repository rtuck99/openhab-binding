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

import com.qubular.binding.googleassistant.internal.config.ChannelMode;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantChannelConfig;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantThingConfig;
import com.qubular.binding.googleassistant.internal.converter.DimmerItemConverter;
import com.qubular.binding.googleassistant.internal.converter.ItemValueConverter;
import com.qubular.binding.googleassistant.internal.converter.NumberItemConverter;
import com.qubular.binding.googleassistant.internal.converter.OnOffValueMappingItemConverter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.ConfigStatusThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link GoogleAssistantThingHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class GoogleAssistantThingHandler extends ConfigStatusThingHandler {
    public static final String CONFIG_QUERY_COMMAND = "queryCommand";
    public static final String CONFIG_ON_COMMAND = "onCommand";
    public static final String CONFIG_OFF_COMMAND = "offCommand";
    public static final String CONFIG_ON_VALUE = "onValue";
    public static final String CONFIG_OFF_VALUE = "offValue";

    private final Logger logger = LoggerFactory.getLogger(GoogleAssistantThingHandler.class);
    private final GoogleAssistantDynamicStateDescriptionProvider googleAssistantDynamicStateDescriptionProvider;

    private GoogleAssistantThingConfig config = new GoogleAssistantThingConfig();
    private final Map<ChannelUID, ItemValueConverter> channels = new HashMap<>();

    private final GoogleAssistantService googleAssistantService;
    private final ThrottlingChannelExecutor throttlingChannelExecutor;

    private Map<String, ConfigStatusMessage> configStatusMessageMap = new ConcurrentHashMap<>();

    public GoogleAssistantThingHandler(Thing thing,
                                       GoogleAssistantDynamicStateDescriptionProvider googleAssistantDynamicStateDescriptionProvider,
                                       GoogleAssistantService googleAssistantService,
                                       ThrottlingChannelExecutor throttlingChannelExecutor) {
        super(thing);
        this.googleAssistantDynamicStateDescriptionProvider = googleAssistantDynamicStateDescriptionProvider;
        this.googleAssistantService = googleAssistantService;
        this.throttlingChannelExecutor = throttlingChannelExecutor;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handling command {} for channel {} from thing {}", command, channelUID, getThing().getUID());
        ItemValueConverter itemValueConverter = channels.get(channelUID);
        if (itemValueConverter == null) {
            logger.warn("Cannot find channel implementation for channel {}.", channelUID);
            return;
        }

        if (command instanceof RefreshType) {
            String queryString = itemValueConverter.generateValueQuery();
            if (queryString != null) {
                CompletableFuture<String> commandFuture = sendCommand(queryString);
                commandFuture
                        .thenApply(response -> {
                            try {
                                return itemValueConverter.convertQueryResponse(response);
                            } catch (ConfigStatusException e) {
                                commandFuture.completeExceptionally(e);
                                return null;
                            }
                        })
                        .thenAccept(state -> updateState(channelUID, state))
                        .exceptionally((e) -> {
                            if (e instanceof ConfigStatusException) {
                                ConfigStatusException cse = (ConfigStatusException) e;
                                handleConfigStatusException(cse);
                            }
                            logger.warn("Unable to fetch value ", e);
                            return null;
                        });
            }
        } else {
            try {
                itemValueConverter.generateCommand(command)
                        .ifPresent(commandString ->
                            throttlingChannelExecutor.submitThrottledCommand(channelUID, new Runnable() {
                                public void run() {
                                    sendCommand(commandString);
                                }

                                public String toString() {
                                    return commandString;
                                }
                        }));
            } catch (IllegalArgumentException e) {
                logger.warn("Failed to convert command '{}' to channel '{}' for sending", command, channelUID);
            } catch (IllegalStateException e) {
                logger.debug("Writing to read-only channel {} not permitted", channelUID);
            }
        }
    }

    private void handleConfigStatusException(ConfigStatusException cse) {
        configStatusMessageMap.put(cse.getConfigStatusMessage().parameterName, cse.getConfigStatusMessage());
        logger.warn("Unable to send command {}", cse.getMessage());
    }

    private CompletableFuture<String> sendCommand(String command) {
        logger.debug("Sending command async {}", command);
        return googleAssistantService.sendCommand(command)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        if (e instanceof ConfigStatusException) {
                            ConfigStatusException cse = (ConfigStatusException) e;
                            handleConfigStatusException(cse);
                        } else {
                            logger.warn("Unable to set value", e);
                        }
                    } else {
                        logger.debug("Command '{}' returned '{}'", command, result);
                    }
                });
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ThingHandler for Google Assistant '{}'.", thing.getUID());

        config = getConfigAs(GoogleAssistantThingConfig.class);

        // create channels
        thing.getChannels().forEach(channel -> createChannel(channel));

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        logger.debug("Disposing ThingHandler for Google Assistant '{}'.", thing.getUID());

        // clear lists
        channels.clear();

        // remove state descriptions
        googleAssistantDynamicStateDescriptionProvider.removeDescriptionsForThing(thing.getUID());

        throttlingChannelExecutor.dispose();

        super.dispose();
    }

    /**
     * create all necessary information to handle every channel
     *
     * @param channel  a thing channel
     */
    private void createChannel(Channel channel) {
        ChannelUID channelUID = channel.getUID();
        GoogleAssistantChannelConfig channelConfig = channel.getConfiguration().as(GoogleAssistantChannelConfig.class);

        String acceptedItemType = channel.getAcceptedItemType();
        if (acceptedItemType == null) {
            logger.warn("Cannot determine item-type for channel '{}'", channelUID);
            return;
        }

        ItemValueConverter itemValueConverter;
        switch (acceptedItemType) {
            case GoogleAssistantBindingConstants.ITEM_TYPE_DIMMER:
                itemValueConverter = new DimmerItemConverter(config, channelConfig);
                break;
            case GoogleAssistantBindingConstants.ITEM_TYPE_SWITCH:
                itemValueConverter = new OnOffValueMappingItemConverter(config, channelConfig);
                break;
            case GoogleAssistantBindingConstants.ITEM_TYPE_NUMBER:
                itemValueConverter = new NumberItemConverter(config, channelConfig);
                break;
            default:
                logger.warn("Unsupported item-type '{}'", channel.getAcceptedItemType());
                return;
        }

        channels.put(channelUID, itemValueConverter);

        StateDescription stateDescription = StateDescriptionFragmentBuilder.create()
                .withReadOnly(channelConfig.mode == ChannelMode.READONLY).build().toStateDescription();
        if (stateDescription != null) {
            // if the state description is not available, we don't need to add it
            googleAssistantDynamicStateDescriptionProvider.setDescription(channelUID, stateDescription);
        }
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        return configStatusMessageMap.values();
    }

    @Override
    protected void updateConfiguration(Configuration configuration) {
        configStatusMessageMap.clear();
        super.updateConfiguration(configuration);
    }
}
