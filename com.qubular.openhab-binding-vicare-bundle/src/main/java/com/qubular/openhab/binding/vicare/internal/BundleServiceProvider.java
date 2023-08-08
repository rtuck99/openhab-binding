package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = VicareServiceProvider.class)
public class BundleServiceProvider implements VicareServiceProvider {
    @Reference
    private VicareService vicareService;
    @Reference
    private ThingRegistry thingRegistry;
    @Reference
    private VicareConfiguration vicareConfiguration;
    @Reference
    private ConfigurationAdmin configurationAdmin;
    @Reference
    private ChannelTypeRegistry channelTypeRegistry;
    @Reference
    private VicareChannelTypeProvider channelTypeProvider;
    @Reference
    private FeatureService featureService;

    private BundleContext bundleContext;

    @Activate
    public BundleServiceProvider(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public VicareService getVicareService() {
        return vicareService;
    }

    @Override
    public VicareChannelTypeProvider getChannelTypeProvider() {
        return channelTypeProvider;
    }

    @Override
    public ThingRegistry getThingRegistry() {
        return thingRegistry;
    }

    @Override
    public VicareConfiguration getVicareConfiguration() {
        return vicareConfiguration;
    }

    @Override
    public String getBindingVersion() {
        return bundleContext.getBundle().getVersion().toString();
    }

    @Override
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    @Override
    public ConfigurationAdmin getConfigurationAdmin() {
        return configurationAdmin;
    }

    @Override
    public ChannelTypeRegistry getChannelTypeRegistry() {
        return channelTypeRegistry;
    }

    @Override
    public FeatureService getFeatureService() {
        return featureService;
    }
}
