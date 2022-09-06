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

import com.qubular.binding.googleassistant.internal.config.GoogleAssistantChannelConfig;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantThingConfig;

/**
 * The {@link AbstractTransformingItemConverter} is a base class for an item converter with transformations
 *
 * @author Jan N. Klug - Initial contribution
 */
public abstract class AbstractTransformingItemConverter implements ItemValueConverter {
    protected GoogleAssistantChannelConfig channelConfig;

    public AbstractTransformingItemConverter(GoogleAssistantThingConfig thingConfig,
                                             GoogleAssistantChannelConfig channelConfig) {
        this.channelConfig = channelConfig;
    }

    @FunctionalInterface
    public interface Factory {
        ItemValueConverter create(GoogleAssistantThingConfig thingConfig,
                                  GoogleAssistantChannelConfig channelConfig);
    }
}
