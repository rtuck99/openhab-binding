package com.qubular.binding.googleassistant.internal.config;

import com.qubular.binding.googleassistant.internal.EmbeddedAssistantService;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.binding.BindingInfo;
import org.openhab.core.binding.BindingInfoRegistry;
import org.openhab.core.config.core.ConfigDescription;
import org.openhab.core.config.core.ConfigDescriptionRegistry;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.net.URI;
import java.util.Dictionary;

@Component(service = GoogleAssistantBindingConfig.class)
public class GoogleAssistantBindingConfig {
    private static final String DEFAULT_DEVICE_ID = "MyFakeSpeaker";
    private static final String DEFAULT_DEVICE_MODEL_ID = "OpenHAB";
    public static final String BINDING_PID = "binding.googleassistant";
    public static final String BINDING_ID = "googleassistant";
    public static final int DEFAULT_API_CHANNEL_THROTTLE_MS = 1000;
    private final Configuration configuration;
    private final BindingInfoRegistry bindingInfoRegistry;
    private final ConfigDescriptionRegistry configDescriptionRegistry;

    @Activate
    public GoogleAssistantBindingConfig(@Reference ConfigurationAdmin configurationAdmin,
                                        @Reference BindingInfoRegistry bindingInfoRegistry,
                                        @Reference ConfigDescriptionRegistry configDescriptionRegistry) {
        this.bindingInfoRegistry = bindingInfoRegistry;
        this.configDescriptionRegistry = configDescriptionRegistry;
        try {
            configuration = configurationAdmin.getConfiguration(BINDING_PID);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private ConfigDescription getConfigDescription() {
        BindingInfo bindingInfo = bindingInfoRegistry.getBindingInfo(BINDING_ID);
        return configDescriptionRegistry.getConfigDescription((@NonNull URI) bindingInfo.getConfigDescriptionURI());
    }

    public String getApiHost() {
        String apiHost = getProperty("apiHost");
        return apiHost == null ? EmbeddedAssistantService.API_HOST : apiHost;
    }

    public Integer getApiPort() {
        Integer apiPort = getProperty("apiPort");
        return apiPort == null ? EmbeddedAssistantService.API_PORT : apiPort;
    }

    public String getDeviceId() {
        String apiHost = getProperty("deviceId");
        return apiHost == null ? DEFAULT_DEVICE_ID : apiHost;
    }

    public String getDeviceModelId() {
        String apiHost = getProperty("deviceModelId");
        return apiHost == null ? DEFAULT_DEVICE_MODEL_ID : apiHost;
    }

    public int getApiChannelThrottleMs() {
        Object objectValue = getProperty("apiChannelThrottleMs");
        return objectValue == null ? DEFAULT_API_CHANNEL_THROTTLE_MS : Integer.parseInt(String.valueOf(objectValue));
    }

    public String getClientCredentials() {
        return getProperty("clientCredentials");
    }

    private <T> T getProperty(String propertyName) {
        Dictionary<String, Object> properties = configuration.getProperties();
        T value = (T) properties.get(propertyName);
        if (value == null) {
            value = (T) getConfigDescription().getParameters().stream()
                    .filter(param -> propertyName.equals(param.getName()))
                    .map(param -> { switch (param.getType()) {
                        case TEXT:
                            return param.getDefault();
                        case INTEGER:
                            return Integer.valueOf(param.getDefault());
                        case BOOLEAN:
                            return Boolean.valueOf(param.getDefault());
                        default:
                            throw new UnsupportedOperationException("Unsupported parameter type " + param.getType());
                    }})
                    .findFirst()
                    .orElse(null);
        }
        return value;
    }
}
