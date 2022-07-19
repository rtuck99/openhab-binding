package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;

public class VicareBridgeHandler extends BaseBridgeHandler {
    /**
     * @param bridge
     * @see BaseThingHandler
     */
    public VicareBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {

    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }
}
