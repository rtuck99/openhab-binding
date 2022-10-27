package com.qubular.vicare.internal;

import com.google.gson.*;
import com.qubular.vicare.*;
import com.qubular.vicare.internal.oauth.AccessGrantResponse;
import com.qubular.vicare.internal.servlet.VicareServlet;
import com.qubular.vicare.model.*;
import com.qubular.vicare.model.features.*;
import com.qubular.vicare.model.params.EnumParamDescriptor;
import com.qubular.vicare.model.ParamDescriptor;
import com.qubular.vicare.model.params.NumericParamDescriptor;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.qubular.vicare.model.Status.*;
import static java.lang.String.format;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.StreamSupport.stream;
import static javax.servlet.http.HttpServletResponse.SC_OK;

@Component(configurationPid = "vicare.bridge")
public class VicareServiceImpl implements VicareService {
    private static final Logger logger = LoggerFactory.getLogger(VicareServiceImpl.class);
    public static final int RATE_LIMIT_EXCEEDED = 429;
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

    private static class CommandResponse {
        public CommandResponseData data;
    }

    private static class CommandResponseData {
        public boolean success;
        public String message;
        public String reason;
    }

    private static class HttpErrorResponse {
        public int statusCode;
        public String message;
        public String errorType;
        public ExtendedPayload extendedPayload;
    }

    private static class ExtendedPayload {
        public long limitReset;
    }

    @Override
    public List<Installation> getInstallations() throws AuthenticationException, IOException {
        logger.trace("Fetching installations.");
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
                maybeInjectInstallations(installations);
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
        try {
            Optional<TokenStore.AccessToken> accessToken = tokenStore.getAccessToken();
            if (!accessToken.isPresent() ||
                    accessToken.get().expiry.isBefore(Instant.now().plusSeconds(60))) {
                String refreshToken = tokenStore.getRefreshToken().orElse(null);
                if (refreshToken == null) {
                    throw new AuthenticationException(
                            "Unable to authenticate: No valid access token and no refresh token.");
                } else {
                    logger.trace("Refreshing access token.");
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
                            Gson gson = new GsonBuilder().setFieldNamingPolicy(
                                    FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
                            AccessGrantResponse accessGrantResponse = gson.fromJson(response.getContentAsString(),
                                                                                    AccessGrantResponse.class);
                            return of(tokenStore.storeAccessToken(accessGrantResponse.accessToken,
                                                                  Instant.now().plusSeconds(
                                                                          accessGrantResponse.expiresIn)));
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
        } catch (GeneralSecurityException e) {
            String msg = format("Unable to obtain access token: %s", e.getMessage());
            logger.warn(msg, e);
            throw new AuthenticationException(msg, e);
        }
    }

    @Override
    public List<Feature> getFeatures(long installationId, String gatewaySerial, String deviceId) throws AuthenticationException, IOException {
        logger.trace("Fetching features for {}/{}", gatewaySerial, deviceId);
        TokenStore.AccessToken accessToken = getValidAccessToken()
                .orElseThrow(()-> new AuthenticationException("No access token for Viessmann API"));

        URI endpoint = URI.create(config.getIOTServerURI())
                .resolve(format("equipment/installations/%s/gateways/%s/devices/%s/features", installationId, gatewaySerial, deviceId));

        try {
            String responseContent = maybeInjectFeatureResponse(installationId, gatewaySerial);
            if (responseContent == null) {
                ContentResponse contentResponse = httpClientProvider.getHttpClient()
                        .newRequest(endpoint)
                        .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken.token)
                        .method(HttpMethod.GET)
                        .send();
                responseContent = contentResponse.getContentAsString();
                maybeCaptureResponse(responseContent);
                if (contentResponse.getStatus() == SC_OK) {
                    return extractFeatures(responseContent);
                } else {
                    String msg = "";
                    try {
                        HttpErrorResponse errorResponse = apiGson().fromJson(responseContent, HttpErrorResponse.class);
                        if (errorResponse != null) {
                            msg = format("Unable to request features from IoT API, server returned %s, %s: %s",
                                                contentResponse.getStatus(),
                                                errorResponse.message,
                                                errorResponse.errorType);
                            if (contentResponse.getStatus() == RATE_LIMIT_EXCEEDED && errorResponse.extendedPayload != null) {
                                logger.warn("Rate limit expires at %s", Instant.ofEpochMilli(errorResponse.extendedPayload.limitReset));
                            }
                            logger.warn(msg);
                            throw new IOException(msg);
                        }
                    } catch (JsonSyntaxException e) {
                        // never mind
                    }
                    throw new IOException("Unable to request features from IoT API, server returned " + contentResponse.getStatus());
                }
            } else {
                return extractFeatures(responseContent);
            }

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Unable to request features from IoT API", e);
            throw new IOException("Unable to request features from IoT API", e);
        }
    }

    @Override
    public void sendCommand(URI uri, Map<String, Object> params) throws AuthenticationException, IOException, CommandFailureException {
        logger.trace("Sending command {}, params {}", uri, params);
        TokenStore.AccessToken accessToken = getValidAccessToken()
                .orElseThrow(()-> new AuthenticationException("No access token for Viessmann API"));

        try {
            Request request = httpClientProvider.getHttpClient()
                    .newRequest(uri)
                    .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken.token)
                    .header(HttpHeader.CONTENT_TYPE, "application/json")
                    .accept("application/json")
                    .method(HttpMethod.POST);
            JsonObject body = new JsonObject();
            params.forEach((name, value) -> {
                if (value instanceof String) {
                    body.addProperty(name, (String) value);
                } else if (value instanceof Number) {
                    body.addProperty(name, (Number) value);
                }
            });
            ContentResponse contentResponse = request.content(new StringContentProvider(apiGson().toJson(body))).send();
            if (contentResponse.getStatus() == SC_OK) {
                CommandResponse commandResponse = apiGson().fromJson(contentResponse.getContentAsString(), CommandResponse.class);
                if (!commandResponse.data.success) {
                    throw new CommandFailureException(commandResponse.data.message, commandResponse.data.reason);
                }
            } else {
                try {
                    HttpErrorResponse errorResponse = apiGson().fromJson(contentResponse.getContentAsString(), HttpErrorResponse.class);
                    String msg = format("Failed to send command, server returned %d, %s - %s", contentResponse.getStatus(), errorResponse.errorType, errorResponse.message);
                    logger.warn(msg);
                    throw new IOException(msg);
                } catch (Exception e) {
                    // never mind
                }
                throw new IOException("Unable to request features from IoT API, server returned " + contentResponse.getStatus());
            }

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Unable to request features from IoT API", e);
            throw new IOException("Unable to request features from IoT API", e);
        }
    }

    private List<Feature> extractFeatures(String responseContent) {
                List<Feature> data = apiGson().fromJson(responseContent, FeatureResponse.class).data;
                return data.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
    }

    /**
     * Inject a debug installation if feature response injection is enabled, as per debug configuration.
     * @param installations
     */
    private void maybeInjectInstallations(InstallationsResponse installations) {
        if (config.isResponseInjectionEnabled() &&
            config.getDebugInjectedInstallationId() != null &&
            config.getDebugInjectedGatewaySerial() != null) {
            installations.data = new ArrayList<>(installations.data);
            installations.data.add(new Installation(config.getDebugInjectedInstallationId(),
                                                    "Injected heating installation",
                                                    List.of(new Gateway(config.getDebugInjectedGatewaySerial(),
                                                                        "1.0",
                                                                        0,
                                                                        Instant.ofEpochMilli(0),
                                                                        "WorksProperly",
                                                                        "Injected Gateway",
                                                                        config.getDebugInjectedInstallationId(),
                                                                        List.of(new Device(config.getDebugInjectedGatewaySerial(),
                                                                                           "0",
                                                                                           "12345678",
                                                                                           "Injected Heating Device",
                                                                                           "Online",
                                                                                           "heating")))),
                                                    "WorksProperly"));
        }
    }

    /**
     * inject the feature response from a file in order to aid debugging.
     * @return the injected or null.
     */
    private String maybeInjectFeatureResponse(long installationId, String gatewaySerial) {
        if (config.isResponseInjectionEnabled() &&
            Objects.equals(config.getDebugInjectedInstallationId(), installationId) &&
            Objects.equals(config.getDebugInjectedGatewaySerial(), gatewaySerial)) {
            try (var fis = new FileInputStream(config.getResponseInjectionFile())) {
                return new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warn("Unable to read response injection file {}: {}", config.getResponseInjectionFile(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * capture the feature response to a file in order to aid debugging.
     * @param responseJson The json response
     */
    private void maybeCaptureResponse(String responseJson) {
        if (config.isResponseCaptureEnabled()) {
            try (var fos = new FileOutputStream(config.getResponseCaptureFile(), false)) {
                fos.write(responseJson.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.warn("Unable to write to capture file {}: {}", config.getResponseCaptureFile(), e.getMessage());
            }
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
            JsonObject commands = jsonObject.getAsJsonObject("commands");
            if (properties != null) {
                JsonObject value = properties.getAsJsonObject("value");
                JsonObject statusObject = properties.getAsJsonObject("status");
                JsonObject activeObject = properties.getAsJsonObject("active");
                if (value != null) {
                    String valueType = value.get("type").getAsString();
                    if ("string".equals(valueType)) {
                        String textValue = value.get("value").getAsString();
                        List<CommandDescriptor> commandDescriptors = generateCommands(commands);
                        return new TextFeature(featureName, textValue, commandDescriptors);
                    } else if ("number".equals(valueType)) {
                        if (statusObject != null) {
                            String status = statusObject.get("value").getAsString();
                            return new NumericSensorFeature(featureName,
                                                            "value", dimensionalValueFromUnitValue(value),
                                                            new Status(status), null);
                        } else {
                            return new NumericSensorFeature(featureName,
                                                            "value", dimensionalValueFromUnitValue(value),
                                                            NA, null);
                        }
                    }
                } else if (featureName.endsWith(".statistics")) {
                    return createMultiValueFeature(featureName, properties, commands);
                } else if (featureName.contains(".consumption.summary")) {
                    Map<String, DimensionalValue> stats = properties.entrySet().stream()
                            .filter(e -> e.getValue().isJsonObject())
                            .filter(e -> "number".equals(e.getValue().getAsJsonObject().get("type").getAsString()))
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    e -> {
                                        JsonObject prop = e.getValue().getAsJsonObject();
                                        return dimensionalValueFromUnitValue(prop);
                                    }));
                    return new ConsumptionFeature(featureName,
                            stats.get("currentDay"),
                            stats.get("lastSevenDays"),
                            stats.get("currentMonth"),
                            stats.get("currentYear"));
                } else if (featureName.contains(".operating.programs.")) {
                    JsonObject temperature = properties.getAsJsonObject("temperature");
                    JsonObject startObject = properties.getAsJsonObject("start");
                    JsonObject endObject = properties.getAsJsonObject("end");
                    if (temperature != null) {
                        boolean activeStatus = activeObject.get("value").getAsBoolean();
                        DimensionalValue temperatureValue = dimensionalValueFromUnitValue(temperature);
                        List<CommandDescriptor> commandDescriptors = generateCommands(commands);
                        return new NumericSensorFeature(featureName, "temperature", commandDescriptors, temperatureValue, NA, activeStatus
                        );
                    } else if (startObject != null && endObject != null) {
                        boolean activeStatus = activeObject.get("value").getAsBoolean();
                        return new DatePeriodFeature(featureName, activeStatus ? ON : OFF, dateFromYYYYMMDD(startObject), dateFromYYYYMMDD(endObject));
                    }
                } else if (featureName.endsWith(".heating.curve")) {
                    JsonObject shiftObject = properties.getAsJsonObject("shift");
                    JsonObject slopeObject = properties.getAsJsonObject("slope");
                    if (shiftObject != null && slopeObject != null) {
                        DimensionalValue shift = dimensionalValueFromUnitValue(shiftObject);
                        DimensionalValue slope = dimensionalValueFromUnitValue(slopeObject);
                        return new CurveFeature(featureName, slope, shift);
                    }
                } else if (statusObject != null || activeObject != null) {
                    Status status = ofNullable(statusObject).map(o -> o.get("value"))
                            .map(JsonElement::getAsString)
                            .map(Status::new)
                            .orElse(NA);
                    Boolean active = ofNullable(activeObject).map(o -> o.get("value"))
                            .map(JsonElement::getAsBoolean)
                            .orElse(null);
                    return new StatusSensorFeature(featureName, status, active);
                } else {
                    return createMultiValueFeature(featureName, properties, commands);
                }
            }
            return null;
        }

        private static MultiValueFeature createMultiValueFeature(String featureName, JsonObject properties, JsonObject commands) {
            Map<String, DimensionalValue> stats = properties.entrySet().stream()
                    .filter(e -> e.getValue().isJsonObject())
                    .filter(e -> "number".equals(e.getValue().getAsJsonObject().get("type").getAsString()))
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> {
                                JsonObject prop = e.getValue().getAsJsonObject();
                                return dimensionalValueFromUnitValue(prop);
                            }));
            return stats.isEmpty() ? null : new MultiValueFeature(featureName, generateCommands(commands), stats);
        }

        private static List<CommandDescriptor> generateCommands(JsonObject commands) {
            return commands.entrySet().stream()
                    .map(e -> generateCommand(e.getKey(), e.getValue().getAsJsonObject()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        private static CommandDescriptor generateCommand(String key, JsonObject value) {
            String name = value.get("name").getAsString();
            boolean executable = value.get("isExecutable").getAsBoolean();
            URI uri = URI.create(value.get("uri").getAsString());
            List<ParamDescriptor> params = value.get("params").getAsJsonObject().entrySet().stream()
                    .map(e -> generateParam(e.getKey(), e.getValue().getAsJsonObject()))
                    .collect(Collectors.toList());

            if (params.contains(null)) {
                // Don't support the command if we don't understand the parameters.
                return null;
            }
            return new CommandDescriptor(name, executable, params, uri);
        }

        private static ParamDescriptor generateParam(String name, JsonObject jsonObject) {
            String type = jsonObject.get("type").getAsString();
            JsonObject constraints = jsonObject.get("constraints").getAsJsonObject();
            switch (type) {
                case "string":
                    if (constraints.has("enum")) {
                        Set<String> enumValues = stream(constraints.getAsJsonArray("enum").spliterator(), false)
                                .map(JsonElement::getAsString)
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                        return new EnumParamDescriptor(jsonObject.get("required").getAsBoolean(), name, enumValues);
                    }
                    break;
                case "number":
                    return new NumericParamDescriptor(jsonObject.get("required").getAsBoolean(),
                                                      name,
                                                      ofNullable(constraints.get("min")).map(JsonElement::getAsDouble).orElse(null),
                                                      ofNullable(constraints.get("max")).map(JsonElement::getAsDouble).orElse(null),
                                                      ofNullable(constraints.get("stepping")).map(JsonElement::getAsDouble).orElse(null));
            }
            logger.trace("Skipping unsupported parameter " + name + ", type " + type);
            return null;
        }
    }

    private static LocalDate dateFromYYYYMMDD(JsonObject prop) {
        JsonElement value = prop.get("value");
        if (value != null) {
            String valueAsString = value.getAsString();
            return valueAsString.isEmpty() ? null : LocalDate.parse(valueAsString);
        }
        return null;
    }

    private static DimensionalValue dimensionalValueFromUnitValue(JsonObject prop) {
        String unit = prop.get("unit").getAsString();
        double numberValue = prop.get("value").getAsDouble();
        return new DimensionalValue(new Unit(unit), numberValue);
    }
}
