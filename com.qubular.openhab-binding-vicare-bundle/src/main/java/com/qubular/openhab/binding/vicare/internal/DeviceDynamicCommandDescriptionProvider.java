package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.params.EnumParamDescriptor;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.type.DynamicCommandDescriptionProvider;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.CommandDescriptionBuilder;
import org.openhab.core.types.CommandOption;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeviceDynamicCommandDescriptionProvider implements DynamicCommandDescriptionProvider,
        ThingHandlerService {
    private VicareDeviceThingHandler handler;

    @Override
    public void setThingHandler(ThingHandler handler) {
        this.handler = (VicareDeviceThingHandler) handler;
    }

    @Override
    public @Nullable VicareDeviceThingHandler getThingHandler() {
        return handler;
    }

    @Override
    public @Nullable CommandDescription getCommandDescription(Channel channel, @Nullable CommandDescription originalCommandDescription, @Nullable Locale locale) {
        if (VicareConstants.BINDING_ID.equals(channel.getUID().getBindingId())) {
            CommandDescriptor commandDescriptor = getCommandDescriptor(channel).orElse(null);
            if (commandDescriptor != null &&
            commandDescriptor.getParams().size() == 1 &&
            commandDescriptor.getParams().get(0) instanceof EnumParamDescriptor) {
                EnumParamDescriptor enumDescriptor = (EnumParamDescriptor) commandDescriptor.getParams().get(0);
                List<CommandOption> options = enumDescriptor.getAllowedValues().stream()
                        .map(value -> new CommandOption(value, value))
                        .collect(Collectors.toList());
                return CommandDescriptionBuilder.create()
                        .withCommandOptions(options)
                        .build();
            }
        }
        return originalCommandDescription;
    }

    private Optional<CommandDescriptor> getCommandDescriptor(Channel channel) {
        return getThingHandler().getBridgeHandler().getCommandDescriptor(channel, null);
    }
}
