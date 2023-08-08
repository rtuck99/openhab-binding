package com.qubular.openhab.binding.vicare;

import com.qubular.openhab.binding.vicare.internal.CachedFeatureService;
import com.qubular.openhab.binding.vicare.internal.FeatureService;
import com.qubular.openhab.binding.vicare.internal.SimpleVicareChannelTypeProvider;
import com.qubular.openhab.binding.vicare.internal.VicareChannelTypeProvider;
import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;

public interface VicareServiceProvider {
    VicareService getVicareService();

    VicareChannelTypeProvider getChannelTypeProvider();

    ThingRegistry getThingRegistry();

    VicareConfiguration getVicareConfiguration();

    String getBindingVersion();

    BundleContext getBundleContext();

    ConfigurationAdmin getConfigurationAdmin();

    ChannelTypeRegistry getChannelTypeRegistry();

    FeatureService getFeatureService();
}
