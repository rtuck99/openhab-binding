package com.qubular.binding.googleassistant.internal;

import com.google.gson.*;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class RestDeviceRegistrationService implements DeviceRegistrationService {
    private final Gson gsonWithUnderscores;
    private final Gson gsonWithCamelCase;

    private HttpClient httpClient;
    private OAuthService oAuthService;
    private OAuthService.ClientCredentials clientCredentials;
    private OAuthService.OAuthSession oAuthSession;

    public RestDeviceRegistrationService(
            HttpClient httpClient,
            OAuthService oAuthService,
            OAuthService.ClientCredentials clientCredentials,
            OAuthService.OAuthSession oAuthSession) {
        this.httpClient = httpClient;
        this.oAuthService = oAuthService;
        this.clientCredentials = clientCredentials;
        this.oAuthSession = oAuthSession;
        gsonWithUnderscores = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        gsonWithCamelCase = new GsonBuilder().create();
    }

    private static class DeviceModels {
        public List<DeviceModel> deviceModels = new ArrayList<>();
    }

    private static class DeviceInstances {
        public List<Device> devices = new ArrayList<>();
    }

    @Override
    public List<DeviceModel> getDeviceModels() throws DeviceRegistrationException {
        ContentResponse response = null;
        try {
            String authorizationHeader = authzHeader();
            response = httpClient.newRequest(projectBaseUri() + "/deviceModels/")
                    .accept("application/json")
                    .header(HttpHeader.AUTHORIZATION, authorizationHeader)
                    .send();
        } catch (ExecutionException e) {
            throw new DeviceRegistrationException("Unable to fetch registered device models.", e.getCause());
        } catch (Exception e) {
            throw new DeviceRegistrationException("Unable to fetch registered device models.", e);
        }
        if (response.getStatus() == 200) {
            try {
                DeviceModels deviceModels = gsonWithCamelCase.fromJson(response.getContentAsString(), DeviceModels.class);
                return deviceModels.deviceModels == null ? Collections.emptyList() : deviceModels.deviceModels;
            } catch (JsonSyntaxException e) {
                return Collections.emptyList();
            }
        } else {
            throw new DeviceRegistrationException("Unable to fetch registered device models, server returned " + response.getStatus());
        }
    }

    private String authzHeader() {
        return String.format("Bearer %s", oAuthService.maybeRefreshAccessToken(oAuthSession).join().getAccessToken());
    }

    private String projectBaseUri() {
        return "https://" + API_HOST + ":" + API_PORT + "/v1alpha2/projects/" + clientCredentials.projectId;
    }

    @Override
    public void addDeviceModel(DeviceModel deviceModel) throws DeviceRegistrationException {
        String json = gsonWithUnderscores.toJson(deviceModel);
        try {
            ContentResponse response = httpClient.POST(projectBaseUri() + "/deviceModels/")
                    .accept("application/json")
                    .header(HttpHeader.AUTHORIZATION, authzHeader())
                    .content(new StringContentProvider("application/json", json, StandardCharsets.UTF_8))
                    .send();
            int status = response.getStatus();
            if (status != 200) {
                throw new DeviceRegistrationException("Unable to add device model, server returned " + status);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new DeviceRegistrationException("Unable to register device model", e);
        }
    }

    @Override
    public List<Device> getDeviceInstances() throws DeviceRegistrationException {
        ContentResponse response = null;
        try {
            String authorizationHeader = authzHeader();
            response = httpClient.newRequest(projectBaseUri() + "/devices/")
                    .accept("application/json")
                    .header(HttpHeader.AUTHORIZATION, authorizationHeader)
                    .send();
        } catch (ExecutionException e) {
            throw new DeviceRegistrationException("Unable to fetch registered device instances.", e.getCause());
        } catch (Exception e) {
            throw new DeviceRegistrationException("Unable to fetch registered device instances.", e);
        }
        if (response.getStatus() == 200) {
            try {
                DeviceInstances deviceModels = gsonWithCamelCase.fromJson(response.getContentAsString(), DeviceInstances.class);
                return deviceModels.devices == null ? Collections.emptyList() : deviceModels.devices;
            } catch (JsonSyntaxException e) {
                return Collections.emptyList();
            }
        } else {
            throw new DeviceRegistrationException("Unable to fetch registered device instances, server returned " + response.getStatus());
        }
    }

    @Override
    public void addDevice(Device deviceInstance) throws DeviceRegistrationException {
        String json = gsonWithUnderscores.toJson(deviceInstance);
        try {
            ContentResponse response = httpClient.POST(projectBaseUri() + "/devices/")
                    .accept("application/json")
                    .header(HttpHeader.AUTHORIZATION, authzHeader())
                    .content(new StringContentProvider("application/json", json, StandardCharsets.UTF_8))
                    .send();
            int status = response.getStatus();
            if (status != 200) {
                throw new DeviceRegistrationException("Unable to add device instance, server returned " + status);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new DeviceRegistrationException("Unable to register device instance", e);
        }
    }
}
