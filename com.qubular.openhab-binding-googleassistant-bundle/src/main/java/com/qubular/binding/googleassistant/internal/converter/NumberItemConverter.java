package com.qubular.binding.googleassistant.internal.converter;

import com.qubular.binding.googleassistant.internal.config.GoogleAssistantChannelConfig;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantThingConfig;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static java.util.Optional.of;

public class NumberItemConverter extends AbstractTransformingItemConverter {
    private static final Logger logger = LoggerFactory.getLogger(NumberItemConverter.class);

    public NumberItemConverter(GoogleAssistantThingConfig thingConfig, GoogleAssistantChannelConfig channelConfig) {
        super(thingConfig, channelConfig);
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
        if (command instanceof Number) {
            Number number = (Number) command;
            String numberCommand = channelConfig.numberCommand;
            if (numberCommand == null) {
                logger.warn("Unable to command number, no number command configured.");
            } else {
                return of(String.format(numberCommand, number.intValue()));
            }
        } else {
            logger.warn("Unsupported command type " + command);
        }
        return Optional.empty();
    }
}
