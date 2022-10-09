package com.qubular.openhab.binding.vicare.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.CommandDescriptionBuilder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.MockitoAnnotations.openMocks;

class DeviceDynamicCommandDescriptionProviderTest {
    @Mock
    private VicareDeviceThingHandler thingHandler;
    private AutoCloseable mockHandle;

    @BeforeEach
    public void setUp() {
        mockHandle = openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockHandle.close();
    }

    @Test
    public void getCommandDescriptionIgnoresUnrelatedChannels() {
        DeviceDynamicCommandDescriptionProvider provider = new DeviceDynamicCommandDescriptionProvider();
        provider.setThingHandler(thingHandler);
        CommandDescription originalDescription = CommandDescriptionBuilder.create().build();
        Channel channel = ChannelBuilder.create(new ChannelUID(new ThingUID(new ThingTypeUID("somebinding", "sometype"), "something"), "somechannel"))
                .build();
        CommandDescription newDescription = provider.getCommandDescription(channel, originalDescription,
                                                                           Locale.getDefault());
        verifyNoInteractions(thingHandler);
        assertSame(originalDescription, newDescription);
    }

}