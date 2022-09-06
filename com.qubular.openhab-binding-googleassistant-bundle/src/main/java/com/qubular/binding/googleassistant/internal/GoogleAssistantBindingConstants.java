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
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link GoogleAssistantBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class GoogleAssistantBindingConstants {
    public static final String ITEM_TYPE_DIMMER = "Dimmer";
    public static final String ITEM_TYPE_SWITCH = "Switch";

    public static final String ITEM_TYPE_NUMBER = "Number";
    private static final String BINDING_ID = "googleassistant";

    public static final ThingTypeUID THING_TYPE_GOOGLEASSISTANT = new ThingTypeUID(BINDING_ID, "googleassistant");
    public static final String CHANNEL_ID_SWITCH = "switch";
}
