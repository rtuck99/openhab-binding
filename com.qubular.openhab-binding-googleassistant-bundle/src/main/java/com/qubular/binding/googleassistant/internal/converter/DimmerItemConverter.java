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

import java.util.Optional;

import com.qubular.binding.googleassistant.internal.config.GoogleAssistantChannelConfig;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantThingConfig;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Optional.*;

/**
 * The {@link DimmerItemConverter} implements {@link org.openhab.core.library.items.DimmerItem} conversions
 *
 * @author Jan N. Klug - Initial contribution
 */

public class DimmerItemConverter extends AbstractTransformingItemConverter {
    Logger logger = LoggerFactory.getLogger(DimmerItemConverter.class);

    public DimmerItemConverter(GoogleAssistantThingConfig thingConfig,
                               GoogleAssistantChannelConfig channelConfig) {
        super(thingConfig, channelConfig);
        this.channelConfig = channelConfig;
    }

    @Override
    public String generateValueQuery() {
        return null;
    }

    @Override
    public State convertQueryResponse(String content) {
        return null;
    }

    @Override
    public Optional<String> generateCommand(Command command) {
        int value;
        if (command instanceof OnOffType) {
            OnOffType onOff = (OnOffType) command;
            command = onOff.as(PercentType.class);
        }
        if (command instanceof IncreaseDecreaseType) {
            IncreaseDecreaseType increaseDecrease = (IncreaseDecreaseType) command;
            if (IncreaseDecreaseType.INCREASE.equals(increaseDecrease)) {
                String increaseCommand = channelConfig.increaseCommand;
                if (increaseCommand == null) {
                    logger.warn("Unable to command dimmer, no increase command configured");
                    return empty();
                }
                return of(increaseCommand);
            } else {
                String decreaseCommand = channelConfig.decreaseCommand;
                if (decreaseCommand == null) {
                    logger.warn("Unable to command dimmer, no decrease command configured");
                    return empty();
                }
                return of(decreaseCommand);
            }
        } else if (command instanceof DecimalType) {
            value = ((PercentType) command).intValue();
        } else {
            throw new UnsupportedOperationException("Unsupported command type " + command);
        }

        String dimmerCommand = channelConfig.dimmerCommand;
        if (dimmerCommand == null) {
            logger.warn("Unable to command dimmer, no dimmer command configured");
            return empty();
        }
        return of(String.format(dimmerCommand, value));
    }
}
