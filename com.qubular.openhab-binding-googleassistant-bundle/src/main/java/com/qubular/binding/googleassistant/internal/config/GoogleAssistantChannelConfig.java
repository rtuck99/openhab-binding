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
package com.qubular.binding.googleassistant.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link GoogleAssistantChannelConfig} class contains fields mapping channel configuration parameters.
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class GoogleAssistantChannelConfig {
    private boolean initialized = false;

    public String stateContent = "";

    public ChannelMode mode = ChannelMode.READWRITE;

    // switch, dimmer
    public @Nullable String onCommand;
    public @Nullable String offCommand;
    public @Nullable String queryCommand;
    public @Nullable String onValue;
    public @Nullable String offValue;

    // dimmer
    public @Nullable String increaseCommand;

    public @Nullable String decreaseCommand;

    public @Nullable String dimmerCommand;

    public @Nullable String numberCommand;
}
