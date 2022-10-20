package com.qubular.openhab.binding.vicare.internal;

import com.qubular.openhab.binding.vicare.VicareServiceProvider;
import com.qubular.vicare.VicareConfiguration;
import com.qubular.vicare.VicareService;
import org.openhab.core.thing.ThingRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventAdmin;

@Component(service = VicareServiceProvider.class)
public class BundleServiceProvider implements VicareServiceProvider {
    @Reference
    private VicareService vicareService;
    @Reference
    private ThingRegistry thingRegistry;
    @Reference
    private VicareConfiguration vicareConfiguration;

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
}
