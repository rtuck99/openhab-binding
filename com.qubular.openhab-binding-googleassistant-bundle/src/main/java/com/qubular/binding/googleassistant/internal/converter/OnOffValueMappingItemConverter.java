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
package com.qubular.binding.googleassistant.internal.converter;

import java.util.Objects;
import java.util.Optional;

import com.qubular.binding.googleassistant.internal.ConfigStatusException;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantChannelConfig;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantThingConfig;
import com.qubular.binding.googleassistant.internal.config.ChannelMode;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 * The {@link OnOffValueMappingItemConverter} implements mapping conversions for different item-types
 *
 * @author Jan N. Klug - Initial contribution
 */

public class OnOffValueMappingItemConverter extends AbstractTransformingItemConverter {

    public OnOffValueMappingItemConverter(GoogleAssistantThingConfig thingConfig,
                                          GoogleAssistantChannelConfig channelConfig) {
        super(thingConfig, channelConfig);
    }

    @Override
    public Optional<String> generateCommand(Command command) {
        if (!(command instanceof OnOffType)) {
            throw new IllegalArgumentException("Switch channel does not accept command type " + command);
        }
        if (channelConfig.mode != ChannelMode.READONLY) {
            return Optional.ofNullable(command == OnOffType.OFF ? channelConfig.offCommand : channelConfig.onCommand);
        } else {
            throw new IllegalStateException("Read-only channel");
        }
    }

    @Override
    public State convertQueryResponse(String content) throws ConfigStatusException {
        if (Objects.equals(content, channelConfig.onValue)) {
            return OnOffType.ON;
        } else if (Objects.equals(content, channelConfig.offValue)) {
            return OnOffType.OFF;
        } else {
            throw new ConfigStatusException(ConfigStatusMessage.Builder.error("onValue")
                    .withMessageKeySuffix("unexpectedResponseFromCommand")
                    .withArguments(content).build());
        }
    }

    @Override
    public String generateValueQuery() {
        return channelConfig.queryCommand;
    }
}
