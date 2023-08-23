package com.qubular.openhab.binding.vicare.internal.configuration;

import com.qubular.openhab.binding.vicare.internal.VicareBridgeHandler;
import com.qubular.vicare.VicareConfiguration;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.io.File;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.qubular.openhab.binding.vicare.internal.VicareBridgeHandler.CONFIG_POLLING_INTERVAL;
import static com.qubular.openhab.binding.vicare.internal.VicareBridgeHandler.DEFAULT_POLLING_INTERVAL;
import static java.util.Optional.ofNullable;

@Component(service = VicareConfiguration.class)
public class SimpleConfiguration implements VicareConfiguration {
    private final BundleContext bundleContext;
    private Map<String, Object> configurationParameters = Collections.emptyMap();

    @Activate
    public SimpleConfiguration(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public String getClientId() {
        return String.valueOf(configurationParameters.get("clientId"));
    }

    @Override
    public String getAccessServerURI() {
        return String.valueOf(ofNullable(configurationParameters.get("accessServerUri")).orElse("https://iam.viessmann.com/idp/v3/token"));
    }

    @Override
    public String getIOTServerURI() {
        return getIOTServerURI(configurationParameters);
    }
    
    private static String getIOTServerURI(Map<String, Object> config) {
        return String.valueOf(ofNullable(config.get("iotServerUri")).orElse("https://api.viessmann.com/iot/"));
    }

    public void setConfigurationParameters(Map<String, Object> configurationParameters) {
        this.configurationParameters = configurationParameters;
    }

    @Override
    public File getResponseCaptureFolder() {
        return bundleContext.getDataFile("captures");
    }

    @Override
    public File getResponseCaptureFile() {
        return new File(getResponseCaptureFolder(), "responseCapture.json");
    }

    @Override
    public boolean isResponseCaptureEnabled() {
        return (Boolean) ofNullable(configurationParameters.get("responseCapture")).orElse(false);
    }

    @Override
    public File getResponseInjectionFile() {
        return bundleContext.getDataFile("responseInjection.json");
    }

    @Override
    public boolean isResponseInjectionEnabled() {
        return (Boolean) ofNullable(configurationParameters.get("responseInjection")).orElse(false);
    }

    @Override
    public Long getDebugInjectedInstallationId() {
        return ofNullable((BigDecimal) configurationParameters.get("injectedInstallationId"))
                .map(BigDecimal::longValue)
                .orElse(null);
    }

    @Override
    public String getDebugInjectedGatewaySerial() {
        return (String) configurationParameters.get("injectedGatewaySerial");
    }

    @Override
    public int getRequestTimeoutSecs() {
        return Math.max(((BigDecimal)configurationParameters.getOrDefault(CONFIG_POLLING_INTERVAL, BigDecimal.valueOf(DEFAULT_POLLING_INTERVAL))).intValue() - 5, 1);
    }

    public static Map<String, Object> upgradeConfiguration(Map<String, Object> oldConfig) {
        Map<String, Object> newConfig = new HashMap<>(oldConfig);
        String currentIOTServerURI = getIOTServerURI(oldConfig);
        String newIOTServerURI = currentIOTServerURI.replaceAll("(.*)/v1/", "$1/");
        newConfig.put("iotServerUri", newIOTServerURI);
        return newConfig;
    }
}
