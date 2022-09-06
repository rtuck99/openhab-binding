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

import com.qubular.binding.googleassistant.internal.ConfigStatusException;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

import java.util.Optional;

/**
 * The {@link ItemValueConverter} defines the interface for converting received content to item state and converting
 * comannds to sending value
 *
 * @author Jan N. Klug - Initial contribution
 */
public interface ItemValueConverter {

    /**
     * Generate the query.
     * @return
     */
    String generateValueQuery();

    /**
     * called to process a given content for this channel
     *
     * @param content content of the HTTP request
     * @return the Google Assistant Query to send
     */
    State convertQueryResponse(String content) throws ConfigStatusException;

    /**
     * called to send a command to this channel
     *
     * @param command
     * @return the Google Assistant command to send.
     */
    Optional<String> generateCommand(Command command);
}
