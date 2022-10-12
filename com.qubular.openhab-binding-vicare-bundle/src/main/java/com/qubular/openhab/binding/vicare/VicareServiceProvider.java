package com.qubular.openhab.binding.vicare;

import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import org.openhab.core.thing.ThingRegistry;

public interface VicareServiceProvider {
    VicareService getVicareService();

    ThingRegistry getThingRegistry();

    VicareConfiguration getVicareConfiguration();

    String getBindingVersion();
}
