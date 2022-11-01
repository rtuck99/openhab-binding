package com.qubular.openhab.binding.vicare;

import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.EventAdmin;

public interface VicareServiceProvider {
    VicareService getVicareService();

    ThingRegistry getThingRegistry();

    VicareConfiguration getVicareConfiguration();

    String getBindingVersion();

    BundleContext getBundleContext();

    ConfigurationAdmin getConfigurationAdmin();

    ChannelTypeRegistry getChannelTypeRegistry();
}
