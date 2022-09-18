package com.qubular.vicare.internal;

import com.google.gson.*;
import com.qubular.vicare.*;
import com.qubular.vicare.internal.oauth.AccessGrantResponse;
import com.qubular.vicare.internal.servlet.VicareServlet;
import com.qubular.vicare.model.*;
import com.qubular.vicare.model.features.*;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.Fields;
import org.osgi.service.component.annotations.*;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.qubular.vicare.model.Status.OFF;
import static com.qubular.vicare.model.Status.ON;
import static java.util.Optional.of;
import static javax.servlet.http.HttpServletResponse.SC_OK;

@Component(configurationPid = "vicare.bridge")
public class VicareServiceImpl implements VicareService {
    private static final Logger logger = LoggerFactory.getLogger(VicareServiceImpl.class);
    private final HttpService httpService;
    private final HttpClientProvider httpClientProvider;
    private final TokenStore tokenStore;
    private final VicareConfiguration config;
    private final VicareServlet vicareServlet;

    @Activate
    public VicareServiceImpl(
            @Reference VicareConfiguration configuration,
            @Reference HttpService httpService,
            @Reference ChallengeStore<?> challengeStore,
            @Reference HttpClientProvider httpClientProvider,
            @Reference TokenStore tokenStore) {
        this.httpService = httpService;
        this.httpClientProvider = httpClientProvider;
        this.tokenStore = tokenStore;
        this.config = configuration;
        logger.info("Activating Viessmann API Service");
        try {
            vicareServlet = new VicareServlet(this, challengeStore, tokenStore, httpClientProvider, config);
            httpService.registerServlet(VicareServlet.CONTEXT_PATH, vicareServlet, new Hashtable<>(), httpService.createDefaultHttpContext());
        } catch (ServletException | NamespaceException e) {
            logger.error("Unable to register Viessmann API servlet", e);
            throw new RuntimeException(e);
        }
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating Viessmann API Service");
        httpService.unregister(VicareServlet.CONTEXT_PATH);
    }

    private static class InstallationsResponse {
        public List<Installation> data;
    }

    private static class FeatureResponse {
        public List<Feature> data;
    }

    @Override
    public List<Installation> getInstallations() throws AuthenticationException, IOException {
        TokenStore.AccessToken accessToken = getValidAccessToken()
                .orElseThrow(()-> new AuthenticationException("No access token for Viessmann API"));

        try {
            URI endpoint = URI.create(config.getIOTServerURI()).resolve("equipment/installations?includeGateways=true");
            logger.debug("Querying {}", endpoint);
            HttpClient httpClient = httpClientProvider.getHttpClient();
            ContentResponse iotApiResponse = httpClient
                    .newRequest(endpoint)
                    .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken.token)
                    .method(HttpMethod.GET)
                    .send();
            if (iotApiResponse.getStatus() == SC_OK) {
                InstallationsResponse installations = apiGson().fromJson(iotApiResponse.getContentAsString(), InstallationsResponse.class);
                return installations.data;
            } else {
                throw new IOException("Unable to fetch installations, server returned " + iotApiResponse.getStatus());
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("Unable to fetch installations.", e);
        }
    }

    private Gson apiGson() {
        return new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .registerTypeAdapter(Instant.class, new InstantDeserializer())
                .registerTypeAdapter(Feature.class, new FeatureDeserializer())
                .create();
    }

    private Optional<TokenStore.AccessToken> getValidAccessToken() throws AuthenticationException {
        Optional<TokenStore.AccessToken> accessToken = tokenStore.getAccessToken();
        if (!accessToken.isPresent() ||
            accessToken.get().expiry.isBefore(Instant.now().plusSeconds(60))) {
            String refreshToken = tokenStore.getRefreshToken().orElse(null);
            if (refreshToken == null) {
                throw new AuthenticationException("Unable to authenticate: No valid access token and no refresh token.");
            } else {
                Fields fields = new Fields();
                fields.put("grant_type", "refresh_token");
                fields.put("client_id", config.getClientId());
                fields.put("refresh_token", refreshToken);
                try {
                    ContentResponse response = httpClientProvider.getHttpClient()
                            .POST(config.getAccessServerURI())
                            .content(new FormContentProvider(fields))
                            .accept("application/json")
                            .send();
                    if (response.getStatus() == 200) {
                        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
                        AccessGrantResponse accessGrantResponse = gson.fromJson(response.getContentAsString(), AccessGrantResponse.class);
                        return of(tokenStore.storeAccessToken(accessGrantResponse.accessToken, Instant.now().plusSeconds(accessGrantResponse.expiresIn)));
                    } else {
                        logger.warn("Unable to refresh, access server sent {}", response.getStatus());
                        throw new AuthenticationException("Unable to refresh access token");
                    }
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    throw new AuthenticationException("Unable to refresh access token", e);
                }
            }
        }
        return accessToken;
    }

    @Override
    public List<Feature> getFeatures(long installationId, String gatewaySerial, String deviceId) throws AuthenticationException, IOException {
        TokenStore.AccessToken accessToken = getValidAccessToken()
                .orElseThrow(()-> new AuthenticationException("No access token for Viessmann API"));

        URI endpoint = URI.create(config.getIOTServerURI())
                .resolve(String.format("equipment/installations/%s/gateways/%s/devices/%s/features", installationId, gatewaySerial, deviceId));

        try {
            ContentResponse contentResponse = httpClientProvider.getHttpClient()
                    .newRequest(endpoint)
                    .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken.token)
                    .method(HttpMethod.GET)
                    .send();

            if (contentResponse.getStatus() == SC_OK) {
                List<Feature> data = apiGson().fromJson(contentResponse.getContentAsString(), FeatureResponse.class).data;
                return data.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            } else {
                throw new IOException("Unable to request features from IoT API, server returned " + contentResponse.getStatus());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Unable to request features from IoT API", e);
            throw new IOException("Unable to request features from IoT API", e);
        }
    }

    private static class InstantDeserializer implements JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return Instant.parse(jsonElement.getAsString());
        }
    }

    private static class FeatureDeserializer implements JsonDeserializer<Feature> {

        @Override
        public Feature deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            if (!jsonObject.get("isEnabled").getAsBoolean()) {
                return null;
            }

            String featureName = jsonObject.get("feature").getAsString();
            JsonObject properties = jsonObject.getAsJsonObject("properties");
            if (properties != null) {
                JsonObject value = properties.getAsJsonObject("value");
                JsonObject statusObject = properties.getAsJsonObject("status");
                if (value != null) {
                    String valueType = value.get("type").getAsString();
                    if ("string".equals(valueType)) {
                        String textValue = value.get("value").getAsString();
                        return new TextFeature(featureName, textValue);
                    } else if ("number".equals(valueType)) {
                        double numberValue = value.get("value").getAsDouble();
                        String unit = value.get("unit").getAsString();
                        if (statusObject != null) {
                            String status = statusObject.get("value").getAsString();
                            return new NumericSensorFeature(featureName,
                                    new DimensionalValue(new Unit(unit), numberValue),
                                    new Status(status));
                        } else {
                            return new NumericSensorFeature(featureName,
                                    new DimensionalValue(new Unit(unit), numberValue),
                                    Status.NA);
                        }
                    }
                } else if (featureName.endsWith(".statistics")) {
                    Map<String, DimensionalValue> stats = properties.entrySet().stream()
                            .filter(e -> e.getValue().isJsonObject())
                            .filter(e -> "number".equals(e.getValue().getAsJsonObject().get("type").getAsString()))
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    e -> {
                                        JsonObject prop = e.getValue().getAsJsonObject();
                                        String unit = prop.get("unit").getAsString();
                                        double numberValue = prop.get("value").getAsDouble();
                                        return new DimensionalValue(new Unit(unit), numberValue);
                                    }));
                    return new StatisticsFeature(featureName, stats);
                } else if (featureName.contains(".consumption.summary")) {
                    Map<String, DimensionalValue> stats = properties.entrySet().stream()
                            .filter(e -> e.getValue().isJsonObject())
                            .filter(e -> "number".equals(e.getValue().getAsJsonObject().get("type").getAsString()))
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    e -> {
                                        JsonObject prop = e.getValue().getAsJsonObject();
                                        String unit = prop.get("unit").getAsString();
                                        double numberValue = prop.get("value").getAsDouble();
                                        return new DimensionalValue(new Unit(unit), numberValue);
                                    }));
                    return new ConsumptionFeature(featureName,
                            stats.get("currentDay"),
                            stats.get("lastSevenDays"),
                            stats.get("currentMonth"),
                            stats.get("currentYear"));
                } else if (featureName.contains(".operating.programs.")) {
                    JsonObject temperature = properties.getAsJsonObject("temperature");
                    if (temperature != null) {
                        JsonObject active = properties.getAsJsonObject("active");
                        boolean activeStatus = active.get("value").getAsBoolean();
                        Unit unit = new Unit(temperature.get("unit").getAsString());
                        double tempValue = temperature.get("value").getAsDouble();
                        DimensionalValue temperatureValue = new DimensionalValue(unit, tempValue);
                        return new NumericSensorFeature(featureName, temperatureValue, activeStatus ? ON : OFF);
                    }
                } else if (statusObject != null) {
                    String status = statusObject.get("value").getAsString();
                    return new StatusSensorFeature(featureName, new Status(status));
                }

            }
            return null;
        }
    }
}
