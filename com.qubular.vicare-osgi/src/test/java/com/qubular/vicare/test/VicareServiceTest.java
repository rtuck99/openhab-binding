package com.qubular.vicare.test;

import com.qubular.vicare.*;
import com.qubular.vicare.model.*;
import com.qubular.vicare.model.features.*;
import com.qubular.vicare.model.params.EnumParamDescriptor;
import com.qubular.vicare.model.params.NumericParamDescriptor;
import com.qubular.vicare.model.values.BooleanValue;
import com.qubular.vicare.model.values.DimensionalValue;
import com.qubular.vicare.model.values.StatusValue;
import com.qubular.vicare.model.values.StringValue;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.opentest4j.AssertionFailedError;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.qubular.vicare.model.values.StatusValue.OFF;
import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.*;

public class VicareServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(VicareServiceTest.class);
    private BundleContext bundleContext;
    private HttpClient httpClient;
    private VicareService vicareService;

    private HttpService httpService;

    private Servlet accessServlet;

    private Servlet iotServlet;
    private SimpleTokenStore tokenStore;

    private <T> T getService(Class<T> clazz) {
        return bundleContext.getService(bundleContext.getServiceReference(clazz));
    }

    @BeforeEach
    public void setUp() throws Exception {
        bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        httpClient = new HttpClient();
        httpClient.start();
        httpService = getService(HttpService.class);
        SimpleConfiguration configuration = (SimpleConfiguration) getService(VicareConfiguration.class);
        String clientId = ofNullable(System.getProperty("com.qubular.vicare.tester.clientId")).orElse("myClientId");
        String accessServerUri = ofNullable(System.getProperty("com.qubular.vicare.tester.accessServerUri")).orElse("http://localhost:9000/grantAccess");
        configuration.setClientId(clientId);
        configuration.setAccessServerURI(accessServerUri);
        configuration.setIOTServerURI("http://localhost:9000/iot/");

        vicareService = getService(VicareService.class);
        tokenStore = (SimpleTokenStore) getService(TokenStore.class);
    }

    boolean realConnection() {
        return Boolean.getBoolean("com.qubular.vicare.tester.realConnection");
    }

    @AfterEach
    public void tearDown() throws Exception {
        httpClient.stop();
        if (accessServlet != null) {
            httpService.unregister("/grantAccess");
            accessServlet = null;
        }
        if (iotServlet != null) {
            httpService.unregister("/iot");
            iotServlet = null;
        }
        tokenStore.reset();
    }

    @Test
    @DisabledIf("realConnection")
    public void setupPageRendersAndIncludesRedirectURI() throws Exception {
        tokenStore.storeAccessToken("mytoken", Instant.now().plus(1, ChronoUnit.DAYS));
        iotServlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                String jsonResponse = new String(getClass().getResourceAsStream("installationsResponse.json").readAllBytes(), StandardCharsets.UTF_8);
                resp.setContentType("application/json");
                resp.setStatus(200);
                try (ServletOutputStream outputStream = resp.getOutputStream()) {
                    outputStream.print(jsonResponse);
                }
            }
        };
        httpService.registerServlet("/iot", iotServlet, new Hashtable<>(), httpService.createDefaultHttpContext());

        String contentAsString = httpClient.GET("http://localhost:9000/vicare/setup")
                .getContentAsString();
        assertTrue(contentAsString.contains("<title>Viessmann API Binding Setup</title>"), contentAsString);
        assertTrue(contentAsString.contains("the following redirect URI: http://localhost:9000/vicare/authCode"), contentAsString);
        Matcher matcher = Pattern.compile("<form action=\"(.*?)\"", Pattern.MULTILINE)
                .matcher(contentAsString);
        assertTrue(matcher.find(), contentAsString);
        URI uri = URI.create(matcher.group(1));
        assertEquals("http", uri.getScheme());
        assertEquals("localhost:9000", uri.getAuthority());
        assertEquals("/vicare/redirect", uri.getPath());
        assertTrue(contentAsString.contains("AUTHORISED"));
        assertTrue(contentAsString.contains("<tr><td>2012616: Test Installation</td><td>7633107093013212: WiFi_SA0041</td><td>0: E3_Vitodens_100_0421</td></tr>"));

        httpClient.setFollowRedirects(false);
        ContentResponse reflectorResponse = httpClient.GET(uri);
        assertEquals(HttpServletResponse.SC_FOUND, reflectorResponse.getStatus());
        URI redirectUri = URI.create(reflectorResponse.getHeaders().get("Location"));
        Map<String, String> queryParams = URIHelper.getQueryParams(redirectUri);
        SimpleChallengeStore challengeStore = (SimpleChallengeStore) getService(ChallengeStore.class);
        assertEquals("http://localhost:9000/vicare/authCode", queryParams.get("redirect_uri"));
        assertEquals("code", queryParams.get("response_type"));
        assertEquals("IoT User offline_access", queryParams.get("scope"));
        assertNotNull(queryParams.get("code_challenge"));
        assertEquals(challengeStore.currentChallenge.getChallengeCode(), queryParams.get("code_challenge"));

    }

    @Test
    @DisabledIf("realConnection")
    public void extractAuthoriseCodeObtainsAnAccessToken() throws ExecutionException, InterruptedException, TimeoutException, ServletException, NamespaceException, IOException, GeneralSecurityException {
        String contentAsString = httpClient.GET("http://localhost:9000/vicare/setup")
                .getContentAsString();
        AtomicBoolean requested = new AtomicBoolean();
        AtomicReference<Map<String,String[]>> parameterMap = new AtomicReference<>();
        accessServlet = new SimpleAccessServer(
                (req, resp) -> {
                    parameterMap.set(req.getParameterMap());
                    requested.set(true);
                    resp.setStatus(200);
                    resp.setContentType("application/json");
                    try (var os = resp.getOutputStream()) {
                        os.print("{\n" +
                                "    \"access_token\": \"eyJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiUlNBLU9BRVAtMjU...\",\n" +
                                "    \"refresh_token\": \"083ed7fe41a619242df5978190fd11b5\",\n" +
                                "    \"token_type\": \"Bearer\",\n" +
                                "    \"expires_in\": 3600\n" +
                                "}");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                }
        );
        httpService.registerServlet("/grantAccess", accessServlet, new Hashtable<>(), httpService.createDefaultHttpContext());

        SimpleChallengeStore challengeStore = (SimpleChallengeStore) getService(ChallengeStore.class);

        System.out.print(contentAsString);
        Matcher matcher = Pattern.compile("<form action=\"(.*?)\"", Pattern.MULTILINE)
                .matcher(contentAsString);
        assertTrue(matcher.find(), contentAsString);

        String reflectorUri = matcher.group(1);
        // This should follow the redirect
        httpClient.setFollowRedirects(false);
        ContentResponse reflectorResponse = httpClient.GET(reflectorUri);
        assertEquals(HttpServletResponse.SC_FOUND, reflectorResponse.getStatus());
        String redirectUri = reflectorResponse.getHeaders().get("Location");

        Map<String, String> queryParams = URIHelper.getQueryParams(URI.create(redirectUri));
        String stateKey = queryParams.get("state");
        // pretend we authorised and call the redirect, this should call our bogus access server
        ContentResponse response = httpClient.GET(
                queryParams.get("redirect_uri") + "?code=abcd1234&state=" + stateKey);
        assertEquals(302, response.getStatus());
        assertEquals("http://localhost:9000/vicare/setup", response.getHeaders().get(HttpHeader.LOCATION));
        assertTrue(requested.get());
        assertArrayEquals(new String[]{"myClientId"}, parameterMap.get().get("client_id"));
        assertArrayEquals(new String[]{queryParams.get("redirect_uri")}, parameterMap.get().get("redirect_uri"));
        assertArrayEquals(new String[]{"authorization_code"}, parameterMap.get().get("grant_type"));
        assertArrayEquals(new String[]{challengeStore.currentChallenge.getChallengeCode()}, parameterMap.get().get("code_verifier"));
        assertArrayEquals(new String[]{"abcd1234"}, parameterMap.get().get("code"));

        TokenStore tokenStore = getService(TokenStore.class);
        assertEquals("eyJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiUlNBLU9BRVAtMjU...", tokenStore.getAccessToken().get().token);
        assertEquals("083ed7fe41a619242df5978190fd11b5", tokenStore.getRefreshToken().get());
    }

    @Test
    @EnabledIf("realConnection")
    public void demoAuthentication() throws InterruptedException {
        Thread.sleep(180000);
    }

    @Test
    @DisabledIf("realConnection")
    public void getInstallationsThrowsIfNoAccessToken() {
        assertThrows(AuthenticationException.class, () -> {
            List<Installation> installations = vicareService.getInstallations();
        });
    }

    @Test
    @DisabledIf("realConnection")
    public void getInstallationsRefreshesAccessToken() throws AuthenticationException, ServletException, NamespaceException, IOException {
        tokenStore.storeAccessToken("mytoken", Instant.now().minus(1, ChronoUnit.SECONDS));
        tokenStore.storeRefreshToken("myrefresh");
        Map<String, String> parameters = new HashMap<>();
        accessServlet = new SimpleAccessServer(
                (req, resp) -> {
                    parameters.put("grant_type", req.getParameter("grant_type"));
                    parameters.put("client_id", req.getParameter("client_id"));
                    parameters.put("refresh_token", req.getParameter("refresh_token"));
                    resp.setStatus(200);
                    resp.setContentType("application/json");
                    try (var os = resp.getOutputStream()) {
                        os.print("{\n" +
                                "    \"access_token\": \"eyJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiUlNBLU9BRVAtMjU...\",\n" +
                                "    \"token_type\": \"Bearer\",\n" +
                                "    \"expires_in\": 3600\n" +
                                "}");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
        httpService.registerServlet("/grantAccess", accessServlet, new Hashtable<>(), httpService.createDefaultHttpContext());
        iotServlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                String jsonResponse = new String(getClass().getResourceAsStream("installationsResponse.json").readAllBytes(), StandardCharsets.UTF_8);

                resp.setContentType("application/json");
                resp.setStatus(200);
                try (ServletOutputStream outputStream = resp.getOutputStream()) {
                    outputStream.print(jsonResponse);
                }
            }
        };
        httpService.registerServlet("/iot", iotServlet, new Hashtable<>(), httpService.createDefaultHttpContext());

        vicareService.getInstallations();

        assertEquals("refresh_token", parameters.get("grant_type"));
        assertEquals("myClientId", parameters.get("client_id"));
        assertEquals("myrefresh", parameters.get("refresh_token"));
        assertEquals("eyJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiUlNBLU9BRVAtMjU...", tokenStore.getAccessToken().get().token);
    }

    @Test
    @DisabledIf("realConnection")
    public void getInstallations() throws ServletException, NamespaceException {
        tokenStore.storeAccessToken("mytoken", Instant.now().plus(1, ChronoUnit.DAYS));
        CompletableFuture<Void> servletTestResult = new CompletableFuture<>();
        iotServlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

                try {
                    assertEquals("/iot/equipment/installations", URI.create(req.getRequestURI()).getPath());
                    assertEquals("true", req.getParameter("includeGateways"));
                    assertEquals("Bearer mytoken", req.getHeader("Authorization"));
                    String jsonResponse = new String(getClass().getResourceAsStream("installationsResponse.json").readAllBytes(), StandardCharsets.UTF_8);

                    resp.setContentType("application/json");
                    resp.setStatus(200);
                    try (ServletOutputStream outputStream = resp.getOutputStream()) {
                        outputStream.print(jsonResponse);
                    }
                    servletTestResult.complete(null);
                } catch (AssertionFailedError e) {
                    servletTestResult.completeExceptionally(e);
                    resp.setStatus(400);
                }
            }
        };
        httpService.registerServlet("/iot", iotServlet, new Hashtable<>(), httpService.createDefaultHttpContext());

        List<Installation> installations = assertDoesNotThrow(() -> vicareService.getInstallations());

        servletTestResult.orTimeout(10, TimeUnit.SECONDS).join();

        assertNotNull(installations);
        assertEquals(1, installations.size());
        Installation installation = installations.get(0);
        assertEquals(2012616, installation.getId());
        assertEquals("Test Installation", installation.getDescription());
        assertEquals("WorksProperly", installation.getAggregatedStatus());
        assertEquals(1, installation.getGateways().size());
        Gateway gateway = installation.getGateways().get(0);
        assertEquals("7633107093013212", gateway.getSerial());
        assertEquals("502.2144.33.0", gateway.getVersion());
        assertEquals(0, gateway.getFirmwareUpdateFailureCounter());
        assertEquals(Instant.parse("2022-07-07T18:54:03.084Z"), gateway.getLastStatusChangedAt());
        assertEquals("WorksProperly", gateway.getAggregatedStatus());
        assertEquals("WiFi_SA0041", gateway.getGatewayType());
        assertEquals(2012616, gateway.getInstallationId());
        assertEquals(1, gateway.getDevices().size());
        Device device = gateway.getDevices().get(0);
        assertEquals("7633107093013212", device.getGatewaySerial());
        assertEquals("0", device.getId());
        assertEquals("7723181102527121", device.getBoilerSerial());
        assertEquals("E3_Vitodens_100_0421", device.getModelId());
        assertEquals("Online", device.getStatus());
        assertEquals("heating", device.getDeviceType());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_boiler_serial() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<Feature> boilerSerial = features.stream()
                .filter(f -> f.getName().equals("heating.boiler.serial"))
                .findFirst();
        assertTrue(boilerSerial.isPresent());
        assertEquals("7723181102527121", ((TextFeature) boilerSerial.get()).getValue());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_boiler_sensors_temperature_commonSupply() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<NumericSensorFeature> commonSupplyTemperature = features.stream()
                .filter(f -> f.getName().equals("heating.boiler.sensors.temperature.commonSupply"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(commonSupplyTemperature.isPresent());
        assertEquals(34.4, commonSupplyTemperature.get().getValue().getValue(), 0.001);
        assertEquals("celsius", commonSupplyTemperature.get().getValue().getUnit().getName());
        assertEquals("connected", commonSupplyTemperature.get().getStatus().getName());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_burners_n_modulation() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<NumericSensorFeature> burnerModulation = features.stream()
                .filter(f -> f.getName().equals("heating.burners.0.modulation"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(burnerModulation.isPresent());
        assertEquals(0, burnerModulation.get().getValue().getValue(), 0.001);
        assertEquals("percent", burnerModulation.get().getValue().getUnit().getName());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_n_circuilation_pump() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<StatusSensorFeature> pumpStatus = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.0.circulation.pump"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(pumpStatus.isPresent());
        assertEquals("off", pumpStatus.get().getStatus().getName());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_n_name() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<TextFeature> nameFeature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.name"))
                .map(TextFeature.class::cast)
                .findFirst();
        assertTrue(nameFeature.isPresent());
        assertEquals("Fussboden", nameFeature.get().getValue());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_n_temperature_levels() throws ServletException, NamespaceException, AuthenticationException, IOException, ExecutionException, InterruptedException, TimeoutException, CommandFailureException {
        List<Feature> features = getFeatures("deviceFeaturesResponse3.json");

        Optional<Feature> temperatureLevels = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.0.temperature.levels"))
                .map(Feature.class::cast)
                .findFirst();
        assertTrue(temperatureLevels.isPresent());
        assertEquals(20, ((DimensionalValue)temperatureLevels.get().getProperties().get("min")).getValue(), 1e-6);
        assertEquals("celsius", ((DimensionalValue)temperatureLevels.get().getProperties().get("min")).getUnit().getName());
        assertEquals(45, ((DimensionalValue)temperatureLevels.get().getProperties().get("max")).getValue(), 1e-6);
        assertEquals("celsius", ((DimensionalValue)temperatureLevels.get().getProperties().get("max")).getUnit().getName());

        assertEquals(3, temperatureLevels.get().getCommands().size());
        Map<String, CommandDescriptor> commands = temperatureLevels.get().getCommands().stream()
                .collect(Collectors.toMap(CommandDescriptor::getName, Function.identity()));
        assertEquals(URI.create("http://localhost:9000/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.circuits.0.temperature.levels/commands/setMin"), commands.get("setMin").getUri());
        assertEquals(false, commands.get("setMin").isExecutable());
        assertEquals(1, commands.get("setMin").getParams().size());
        assertEquals("temperature", commands.get("setMin").getParams().get(0).getName());
        assertEquals(true, commands.get("setMin").getParams().get(0).isRequired());
        assertEquals(20, ((NumericParamDescriptor)commands.get("setMin").getParams().get(0)).getMin(), 1e-6);
        assertEquals(20, ((NumericParamDescriptor)commands.get("setMin").getParams().get(0)).getMax(), 1e-6);
        assertEquals(true, commands.get("setMax").isExecutable());
        assertEquals(1, commands.get("setMax").getParams().size());
        assertEquals(21, ((NumericParamDescriptor)commands.get("setMax").getParams().get(0)).getMin(), 1e-6);
        assertEquals(80, ((NumericParamDescriptor)commands.get("setMax").getParams().get(0)).getMax(), 1e-6);
        assertEquals("temperature", commands.get("setMax").getParams().get(0).getName());
        assertEquals(true, commands.get("setMax").getParams().get(0).isRequired());
        assertEquals(true, commands.get("setLevels").isExecutable());
        assertEquals(2, commands.get("setLevels").getParams().size());
        assertEquals("minTemperature", commands.get("setLevels").getParams().get(0).getName());
        assertEquals(true, commands.get("setLevels").getParams().get(0).isRequired());
        assertEquals(20, ((NumericParamDescriptor)commands.get("setLevels").getParams().get(0)).getMin(), 1e-6);
        assertEquals(20, ((NumericParamDescriptor)commands.get("setLevels").getParams().get(0)).getMax(), 1e-6);
        assertEquals("maxTemperature", commands.get("setLevels").getParams().get(1).getName());
        assertEquals(true, commands.get("setLevels").getParams().get(1).isRequired());
        assertEquals(21, ((NumericParamDescriptor)commands.get("setLevels").getParams().get(1)).getMin(), 1e-6);
        assertEquals(80, ((NumericParamDescriptor)commands.get("setLevels").getParams().get(1)).getMax(), 1e-6);

        CompletableFuture<String> requestContent = new CompletableFuture<>();

        iotServlet = new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("application/json", req.getHeader(HttpHeader.CONTENT_TYPE.asString()));
                    assertEquals("application/json", req.getHeader(HttpHeader.ACCEPT.asString()));
                } catch (AssertionFailedError e) {
                    requestContent.completeExceptionally(e);
                }
                requestContent.complete(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                String jsonResponse = "{\"data\":{\"success\":true,\"reason\":\"COMMAND_EXECUTION_SUCCESS\"}}";
                resp.setContentType("application/json");
                resp.setStatus(200);
                try (ServletOutputStream outputStream = resp.getOutputStream()) {
                    outputStream.print(jsonResponse);
                }
            }
        };
        httpService.registerServlet("/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.circuits.0.temperature.levels/commands/setMax", iotServlet, new Hashtable<>(), httpService.createDefaultHttpContext());

        vicareService.sendCommand(URI.create("http://localhost:9000/iot/v1/equipment/installations/1234567/gateways/1234567890123456/devices/0/features/heating.circuits.0.temperature.levels/commands/setMax"),
                                  Map.of("temperature", 46));
        assertEquals("{\"temperature\":46}", requestContent.get(1, TimeUnit.SECONDS));
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_burners_n_statistics() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<Feature> burnerStats = features.stream()
                .filter(f -> f.getName().equals("heating.burners.0.statistics"))
                .findFirst();
        assertTrue(burnerStats.isPresent());
        assertEquals("hour", ((DimensionalValue)burnerStats.get().getProperties().get("hours")).getUnit().getName());
        assertEquals(5, ((DimensionalValue)burnerStats.get().getProperties().get("hours")).getValue());
        assertEquals("", ((DimensionalValue)burnerStats.get().getProperties().get("starts")).getUnit().getName());
        assertEquals(312, ((DimensionalValue)burnerStats.get().getProperties().get("starts")).getValue());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_power_consumption_dhw() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<ConsumptionFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.power.consumption.dhw"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();

        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getValue(), 0.01f);
        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_DAY).get().getValue(), 0.01f);
        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_WEEK).get().getValue(), 0.01f);
        assertEquals(0.1, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_WEEK).get().getValue(), 0.01f);
        assertEquals(0.7, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_MONTH).get().getValue(), 0.01f);
        assertEquals(1.1, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_MONTH).get().getValue(), 0.01f);
        assertEquals(25.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_YEAR).get().getValue(), 0.01f);
        assertEquals(23.0, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_YEAR).get().getValue(), 0.01f);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_power_consumption_heating() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<ConsumptionFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.power.consumption.heating"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();

        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getValue(), 0.01f);
        assertEquals(0.1, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_DAY).get().getValue(), 0.01f);
        assertEquals(0.3, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_WEEK).get().getValue(), 0.01f);
        assertEquals(0.7, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_WEEK).get().getValue(), 0.01f);
        assertEquals(2.8, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_MONTH).get().getValue(), 0.01f);
        assertEquals(3.5, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_MONTH).get().getValue(), 0.01f);
        assertEquals(34.1, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_YEAR).get().getValue(), 0.01f);
        assertEquals(118.2, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_YEAR).get().getValue(), 0.01f);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_power_consumption_total() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<ConsumptionFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.power.consumption.total"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();

        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getValue(), 0.01f);
        assertEquals(0.1, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_DAY).get().getValue(), 0.01f);
        assertEquals(0.3, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_WEEK).get().getValue(), 0.01f);
        assertEquals(0.8, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_WEEK).get().getValue(), 0.01f);
        assertEquals(3.5, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_MONTH).get().getValue(), 0.01f);
        assertEquals(4.6, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_MONTH).get().getValue(), 0.01f);
        assertEquals(59.1, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_YEAR).get().getValue(), 0.01f);
        assertEquals(141.2, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_YEAR).get().getValue(), 0.01f);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_power_consumption_summary_dhw() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<ConsumptionSummaryFeature> dhwConsumption = features.stream()
                .filter(f -> f.getName().equals("heating.power.consumption.summary.dhw"))
                .map(ConsumptionSummaryFeature.class::cast)
                .findFirst();
        assertTrue(dhwConsumption.isPresent());
        assertEquals("kilowattHour", ((ConsumptionFeature) dhwConsumption.get()).getConsumption(
                ConsumptionFeature.Stat.CURRENT_DAY).orElse(null).getUnit().getName());
        assertEquals(0, ((ConsumptionFeature) dhwConsumption.get()).getConsumption(
                ConsumptionFeature.Stat.CURRENT_DAY).orElse(null).getValue());
        assertEquals("kilowattHour", dhwConsumption.get().getSevenDays().getUnit().getName());
        assertEquals(0.2, dhwConsumption.get().getSevenDays().getValue(), 0.001);
        assertEquals("kilowattHour", ((ConsumptionFeature) dhwConsumption.get()).getConsumption(
                ConsumptionFeature.Stat.CURRENT_MONTH).orElse(null).getUnit().getName());
        assertEquals(0.2, ((ConsumptionFeature) dhwConsumption.get()).getConsumption(
                ConsumptionFeature.Stat.CURRENT_MONTH).orElse(null).getValue(), 0.001);
        assertEquals("kilowattHour", ((ConsumptionFeature) dhwConsumption.get()).getConsumption(
                ConsumptionFeature.Stat.CURRENT_YEAR).orElse(null).getUnit().getName());
        assertEquals(0.9, ((ConsumptionFeature) dhwConsumption.get()).getConsumption(
                ConsumptionFeature.Stat.CURRENT_YEAR).orElse(null).getValue(), 0.001);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_0_operating_modes_active() throws ServletException, AuthenticationException, NamespaceException, IOException, CommandFailureException, ExecutionException, InterruptedException, TimeoutException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<TextFeature> modeActive = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.0.operating.modes.active"))
                .map(TextFeature.class::cast)
                .findFirst();

        assertTrue(modeActive.isPresent());
        assertEquals("dhw", modeActive.get().getValue());
        assertEquals(1, modeActive.get().getCommands().size());
        assertEquals("setMode", modeActive.get().getCommands().get(0).getName());
        assertEquals(true, modeActive.get().getCommands().get(0).isExecutable());
        assertEquals("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.modes.active/commands/setMode", modeActive.get().getCommands().get(0).getUri().toString());
        assertEquals(1, modeActive.get().getCommands().get(0).getParams().size());
        EnumParamDescriptor modeParam = (EnumParamDescriptor) modeActive.get().getCommands().get(0).getParams().get(0);
        assertEquals(Set.of("standby", "heating", "dhw", "dhwAndHeating"), modeParam.getAllowedValues());
        assertEquals(true, modeParam.isRequired());
        assertEquals("mode", modeParam.getName());
        assertTrue(modeParam.validate("standby"));
        assertFalse(modeParam.validate("off"));

        CompletableFuture<String> requestContent = new CompletableFuture<>();

        iotServlet = new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("application/json", req.getHeader(HttpHeader.CONTENT_TYPE.asString()));
                    assertEquals("application/json", req.getHeader(HttpHeader.ACCEPT.asString()));
                } catch (AssertionFailedError e) {
                    requestContent.completeExceptionally(e);
                }
                requestContent.complete(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                String jsonResponse = "{\"data\":{\"success\":true,\"reason\":\"COMMAND_EXECUTION_SUCCESS\"}}";
                resp.setContentType("application/json");
                resp.setStatus(200);
                try (ServletOutputStream outputStream = resp.getOutputStream()) {
                    outputStream.print(jsonResponse);
                }
            }
        };
        httpService.registerServlet("/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.modes.active/commands/setMode", iotServlet, new Hashtable<>(), httpService.createDefaultHttpContext());

        vicareService.sendCommand(URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.modes.active/commands/setMode"),
                                  Map.of("mode", "dhwAndHeating"));
        assertEquals("{\"mode\":\"dhwAndHeating\"}", requestContent.get(1, TimeUnit.SECONDS));
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_N_operating_modes_dhw() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.operating.modes.dhw"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(Boolean.FALSE, feature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_N_operating_modes_dhwAndHeating() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.operating.modes.dhwAndHeating"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(Boolean.TRUE, feature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_N_operating_modes_standby() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.operating.modes.standby"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(Boolean.FALSE, feature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_N_operating_programs_forcedLastFromSchedule() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.operating.programs.forcedLastFromSchedule"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(Boolean.FALSE, feature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_0_operating_programs_normal() throws ServletException, NamespaceException, AuthenticationException, IOException, CommandFailureException, ExecutionException, InterruptedException, TimeoutException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<NumericSensorFeature> normalMode = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.0.operating.programs.normal"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(normalMode.isPresent());
        assertEquals(false, normalMode.get().isActive());
        assertEquals(20, normalMode.get().getValue().getValue(), 0.001);
        assertEquals("celsius", normalMode.get().getValue().getUnit().getName());
        assertEquals(1, normalMode.get().getCommands().size());
        assertEquals(URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normal/commands/setTemperature"), normalMode.get().getCommands().get(0).getUri());
        assertEquals("setTemperature", normalMode.get().getCommands().get(0).getName());
        assertEquals(1, normalMode.get().getCommands().get(0).getParams().size());
        assertEquals("targetTemperature", normalMode.get().getCommands().get(0).getParams().get(0).getName());
        assertEquals(true, normalMode.get().getCommands().get(0).getParams().get(0).isRequired());
        assertEquals(3, ((NumericParamDescriptor) normalMode.get().getCommands().get(0).getParams().get(0)).getMin());
        assertEquals(37, ((NumericParamDescriptor) normalMode.get().getCommands().get(0).getParams().get(0)).getMax());
        assertEquals(1, ((NumericParamDescriptor) normalMode.get().getCommands().get(0).getParams().get(0)).getStepping());

        CompletableFuture<String> requestContent = new CompletableFuture<>();
        iotServlet = new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("application/json", req.getHeader(HttpHeader.CONTENT_TYPE.asString()));
                    assertEquals("application/json", req.getHeader(HttpHeader.ACCEPT.asString()));
                } catch (AssertionFailedError e) {
                    requestContent.completeExceptionally(e);
                }
                requestContent.complete(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                String jsonResponse = "{\"data\":{\"success\":true,\"reason\":\"COMMAND_EXECUTION_SUCCESS\"}}";
                resp.setContentType("application/json");
                resp.setStatus(200);
                try (ServletOutputStream outputStream = resp.getOutputStream()) {
                    outputStream.print(jsonResponse);
                }
            }
        };
        httpService.registerServlet("/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normal/commands/setTemperature", iotServlet, new Hashtable<>(), httpService.createDefaultHttpContext());

        vicareService.sendCommand(URI.create("http://localhost:9000/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.circuits.0.operating.programs.normal/commands/setTemperature"),
                                  Map.of("targetTemperature", 23.0));
        assertEquals("{\"targetTemperature\":23.0}", requestContent.get(1, TimeUnit.SECONDS));
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_N_operating_programs_reducedEnergySaving() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<Feature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.operating.programs.reducedEnergySaving"))
                .map(Feature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(BooleanValue.FALSE, feature.get().getProperties().get("active"));
        assertEquals(new StringValue("unknown"), feature.get().getProperties().get("reason"));
        assertEquals(new StringValue("heating"), feature.get().getProperties().get("demand"));
    }

    @Test
    @DisabledIf("realConnection")
    public void suppports_heating_circuits_N_operating_programs_standby() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.operating.programs.standby"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(BooleanValue.FALSE, feature.get().getProperties().get("active"));
    }

    @Test
    @DisabledIf("realConnection")
    public void suppports_heating_circuits_N_operating_programs_summerEco() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.operating.programs.summerEco"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(BooleanValue.FALSE, feature.get().getProperties().get("active"));
    }

    @Test
    @DisabledIf("realConnection")
    public void suppports_heating_circuits_N_sensors_temperature_supply() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<NumericSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.sensors.temperature.supply"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(new StatusValue("connected"), feature.get().getStatus());
        assertEquals(new DimensionalValue(Unit.CELSIUS, 24.6), feature.get().getValue());
    }

    @Test
    @DisabledIf("realConnection")
    public void suppports_heating_circuits_N_zone_mode() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.1.zone.mode"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(feature.isPresent());
        assertEquals(StatusValue.NA, feature.get().getStatus());
        assertEquals(false, feature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_burners_n() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<StatusSensorFeature> burnerFeature = features.stream()
                .filter(f -> f.getName().equals("heating.burners.0"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(burnerFeature.isPresent());
        assertEquals(false, burnerFeature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_circuits_n_heating_curve() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<CurveFeature> curveFeature = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.0.heating.curve"))
                .map(CurveFeature.class::cast)
                .findFirst();
        assertTrue(curveFeature.isPresent());
        assertEquals(0, curveFeature.get().getShift().getValue(), 0.01);
        assertEquals(2, curveFeature.get().getSlope().getValue(), 0.01);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_operating_programs_holiday() throws ServletException, NamespaceException, AuthenticationException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<DatePeriodFeature> dateFeature = features.stream()
                .filter(f -> f.getName().equals("heating.operating.programs.holiday"))
                .map(DatePeriodFeature.class::cast)
                .findFirst();
        assertTrue(dateFeature.isPresent());
        assertEquals(LocalDate.parse("2022-12-23"), dateFeature.get().getStart());
        assertEquals(LocalDate.parse("2022-12-26"), dateFeature.get().getEnd());
        assertEquals(OFF, dateFeature.get().getActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_operating_programs_holidayAtHome() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<DatePeriodFeature> dateFeature = features.stream()
                .filter(f -> f.getName().equals("heating.operating.programs.holidayAtHome"))
                .map(DatePeriodFeature.class::cast)
                .findFirst();
        assertTrue(dateFeature.isPresent());
        assertEquals(null, dateFeature.get().getStart());
        assertEquals(null, dateFeature.get().getEnd());
        assertEquals(OFF, dateFeature.get().getActive());
    }

    private List<Feature> getFeatures(final String fileName) throws ServletException, NamespaceException, AuthenticationException, IOException {
        CompletableFuture<Void> servletTestResult = new CompletableFuture<>();
        tokenStore.storeAccessToken("mytoken", Instant.now().plus(1, ChronoUnit.DAYS));
        iotServlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

                try {
                    assertEquals("/iot/equipment/installations/2012616/gateways/7633107093013212/devices/0/features", URI.create(req.getRequestURI()).getPath());
                    assertEquals("Bearer mytoken", req.getHeader("Authorization"));
                    String jsonResponse = new String(getClass().getResourceAsStream(fileName).readAllBytes(), StandardCharsets.UTF_8);

                    resp.setContentType("application/json");
                    resp.setStatus(200);
                    try (ServletOutputStream outputStream = resp.getOutputStream()) {
                        outputStream.print(jsonResponse);
                    }
                    servletTestResult.complete(null);
                } catch (AssertionFailedError e) {
                    servletTestResult.completeExceptionally(e);
                    resp.setStatus(400);
                }
            }
        };
        httpService.registerServlet("/iot", iotServlet, new Hashtable<>(), httpService.createDefaultHttpContext());
        List<Feature> features = vicareService.getFeatures(2012616, "7633107093013212", "0");

        servletTestResult.orTimeout(10, TimeUnit.SECONDS).join();
        return features;
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse2.json");

        Optional<StatusSensorFeature> dhwFeature = features.stream()
                .filter(f -> f.getName().equals("heating.dhw"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(dhwFeature.isPresent());
        assertEquals(StatusValue.ON, dhwFeature.get().getStatus());
        assertEquals(true, dhwFeature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw_hygiene() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<Feature> dhwFeature = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.hygiene"))
                .findFirst();
        assertTrue(dhwFeature.isPresent());
        assertEquals(BooleanValue.FALSE, dhwFeature.get().getProperties().get("enabled"));
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw_oneTimeCharge() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> dhwFeature = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.oneTimeCharge"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(dhwFeature.isPresent());
        assertFalse(dhwFeature.get().isActive());
        assertEquals(2, dhwFeature.get().getCommands().size());
        assertEquals("activate", dhwFeature.get().getCommands().get(0).getName());
        assertTrue(dhwFeature.get().getCommands().get(0).getParams().isEmpty());
        assertEquals(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/activate"), dhwFeature.get().getCommands().get(0).getUri());
        assertEquals("deactivate", dhwFeature.get().getCommands().get(1).getName());
        assertTrue(dhwFeature.get().getCommands().get(1).getParams().isEmpty());
        assertEquals(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/123456/gateways/00/devices/0/features/heating.dhw.oneTimeCharge/commands/deactivate"), dhwFeature.get().getCommands().get(1).getUri());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw_operating_modes_off() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> dhwFeature = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.operating.modes.off"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(dhwFeature.isPresent());
        assertFalse(dhwFeature.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw_pumps_primary() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse2.json");

        Optional<StatusSensorFeature> dhwFeature = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.pumps.primary"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(dhwFeature.isPresent());
        assertEquals(StatusValue.OFF, dhwFeature.get().getStatus());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw_pumps_circulation() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse2.json");

        Optional<StatusSensorFeature> dhwFeature = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.pumps.circulation"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(dhwFeature.isPresent());
        assertEquals(StatusValue.ON, dhwFeature.get().getStatus());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw_sensors_temperature_hotWaterStorage() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse2.json");

        Optional<NumericSensorFeature> tempSensor = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.sensors.temperature.hotWaterStorage"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(tempSensor.isPresent());
        assertEquals(54.3, tempSensor.get().getValue().getValue(), 0.01);
        assertEquals(new Unit("celsius"), tempSensor.get().getValue().getUnit());
        assertEquals(new StatusValue("connected"), tempSensor.get().getStatus());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_dhw_temperature_main() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<NumericSensorFeature> mainTemp = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.temperature.main"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(mainTemp.isPresent());
        assertEquals(50.0, mainTemp.get().getValue().getValue(), 0.01);
        assertEquals(new Unit("celsius"), mainTemp.get().getValue().getUnit());
        assertEquals(1, mainTemp.get().getCommands().size());
        CommandDescriptor command = mainTemp.get().getCommands().get(0);
        assertEquals("setTargetTemperature", command.getName());
        assertEquals(1, command.getParams().size());
        assertTrue(command.isExecutable());
        assertEquals(URI.create("https://api.viessmann.com/iot/v1/equipment/installations/2012616/gateways/7633107093013212/devices/0/features/heating.dhw.temperature.main/commands/setTargetTemperature"), command.getUri());
        NumericParamDescriptor param = (NumericParamDescriptor) command.getParams().get(0);
        assertEquals("temperature", param.getName());
        assertEquals(Double.class, param.getType());
        assertTrue(param.isRequired());
        assertEquals(30, param.getMin());
        assertEquals(60, param.getMax());
        assertEquals(1, param.getStepping());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heat_dhw_sensors_temperature_outlet() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse.json");

        Optional<NumericSensorFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.dhw.sensors.temperature.outlet"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();

        assertEquals(27.3, feature.get().getValue().getValue(), 0.01f);
        assertEquals(Unit.CELSIUS, feature.get().getValue().getUnit());
        assertEquals(new StatusValue("connected"), feature.get().getStatus());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_gas_consumption_dhw() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<ConsumptionFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.gas.consumption.dhw"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();

        assertEquals(1.7, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getValue(), 0.01f);
        assertEquals(1.2, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_DAY).get().getValue(), 0.01f);
        assertEquals(4.6, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_WEEK).get().getValue(), 0.01f);
        assertEquals(17.6, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_WEEK).get().getValue(), 0.01f);
        assertEquals(22.2, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_MONTH).get().getValue(), 0.01f);
        assertEquals(33.5, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_MONTH).get().getValue(), 0.01f);
        assertEquals(730.9, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_YEAR).get().getValue(), 0.01f);
        assertEquals(676.3, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_YEAR).get().getValue(), 0.01f);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_gas_consumption_heating() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<ConsumptionFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.gas.consumption.heating"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();

        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getValue(), 0.01f);
        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_DAY).get().getValue(), 0.01f);
        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_WEEK).get().getValue(), 0.01f);
        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_WEEK).get().getValue(), 0.01f);
        assertEquals(3.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_MONTH).get().getValue(), 0.01f);
        assertEquals(0.0, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_MONTH).get().getValue(), 0.01f);
        assertEquals(3.0, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_YEAR).get().getValue(), 0.01f);
        assertEquals(871.3, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_YEAR).get().getValue(), 0.01f);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_gas_consumption_total() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<ConsumptionFeature> feature = features.stream()
                .filter(f -> f.getName().equals("heating.gas.consumption.total"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();

        assertEquals(1.7, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getValue(), 0.01f);
        assertEquals(1.2, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_DAY).get().getValue(), 0.01f);
        assertEquals(4.6, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_WEEK).get().getValue(), 0.01f);
        assertEquals(17.6, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_WEEK).get().getValue(), 0.01f);
        assertEquals(25.2, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_MONTH).get().getValue(), 0.01f);
        assertEquals(33.5, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_MONTH).get().getValue(), 0.01f);
        assertEquals(733.9, feature.get().getConsumption(ConsumptionFeature.Stat.CURRENT_YEAR).get().getValue(), 0.01f);
        assertEquals(1547.6, feature.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_YEAR).get().getValue(), 0.01f);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_solar_power_production() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<ConsumptionFeature> solarPower = features.stream()
                .filter(f -> f.getName().equals("heating.solar.power.production"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();

        assertEquals(0.0, solarPower.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getValue(), 0.001);
        assertEquals(Unit.KILOWATT_HOUR, solarPower.get().getConsumption(ConsumptionFeature.Stat.CURRENT_DAY).get().getUnit());
        assertEquals(11.4, solarPower.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_DAY).get().getValue(), 0.001);
        assertEquals(31, solarPower.get().getConsumption(ConsumptionFeature.Stat.CURRENT_WEEK).get().getValue(), 0.001);
        assertEquals(58.3, solarPower.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_WEEK).get().getValue(), 0.001);
        assertEquals(247.8, solarPower.get().getConsumption(ConsumptionFeature.Stat.CURRENT_MONTH).get().getValue(), 0.001);
        assertEquals(355.5, solarPower.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_MONTH).get().getValue(), 0.001);
        assertEquals(4250.6, solarPower.get().getConsumption(ConsumptionFeature.Stat.CURRENT_YEAR).get().getValue(), 0.001);
        assertEquals(0.0, solarPower.get().getConsumption(ConsumptionFeature.Stat.PREVIOUS_YEAR).get().getValue(), 0.001);
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_solar_sensors_temperature_collector() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<NumericSensorFeature> temp = features.stream()
                .filter(f -> f.getName().equals("heating.solar.sensors.temperature.collector"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(temp.isPresent());
        assertEquals(35.4, temp.get().getValue().getValue(), 0.01);
        assertEquals(new Unit("celsius"), temp.get().getValue().getUnit());
        assertEquals(new StatusValue("connected"), temp.get().getStatus());
    }

    @Test
    @DisabledIf("realConnection")
    public void support_heating_solar_pumps_circuit() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> status = features.stream()
                .filter(f -> f.getName().equals("heating.solar.pumps.circuit"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(status.isPresent());
        assertEquals(StatusValue.OFF, status.get().getStatus());
        assertNull(status.get().isActive());
    }

    @Test
    @DisabledIf("realConnection")
    public void supports_heating_solar() throws ServletException, AuthenticationException, NamespaceException, IOException {
        List<Feature> features = getFeatures("deviceFeaturesResponse4.json");

        Optional<StatusSensorFeature> status = features.stream()
                .filter(f -> f.getName().equals("heating.solar"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();

        assertTrue(status.isPresent());
        assertEquals(true, status.get().isActive());
        assertEquals(StatusValue.NA, status.get().getStatus());
    }
}
