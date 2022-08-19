package com.qubular.vicare.test;

import com.qubular.vicare.*;
import com.qubular.vicare.model.*;
import com.qubular.vicare.model.features.*;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.opentest4j.AssertionFailedError;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationAdmin;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public void extractAuthoriseCodeObtainsAnAccessToken() throws ExecutionException, InterruptedException, TimeoutException, ServletException, NamespaceException, IOException {
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
        int status = httpClient.GET(queryParams.get("redirect_uri") + "?code=abcd1234&state=" + stateKey).getStatus();
        assertEquals(200, status);
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
    public void getDeviceFeatures() throws ServletException, NamespaceException, AuthenticationException, IOException {

        CompletableFuture<Void> servletTestResult = new CompletableFuture<>();
        tokenStore.storeAccessToken("mytoken", Instant.now().plus(1, ChronoUnit.DAYS));
        iotServlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

                try {
                    assertEquals("/iot/equipment/installations/2012616/gateways/7633107093013212/devices/0/features", URI.create(req.getRequestURI()).getPath());
                    assertEquals("Bearer mytoken", req.getHeader("Authorization"));
                    String jsonResponse = new String(getClass().getResourceAsStream("deviceFeaturesResponse.json").readAllBytes(), StandardCharsets.UTF_8);

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

        assertTrue(features.size() > 0);

        Optional<Feature> boilerSerial = features.stream()
                .filter(f -> f.getName().equals("heating.boiler.serial"))
                .findFirst();
        assertTrue(boilerSerial.isPresent());
        assertEquals("7723181102527121", ((TextFeature)boilerSerial.get()).getValue());

        Optional<NumericSensorFeature> commonSupplyTemperature = features.stream()
                .filter(f -> f.getName().equals("heating.boiler.sensors.temperature.commonSupply"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(commonSupplyTemperature.isPresent());
        assertEquals(34.4, commonSupplyTemperature.get().getValue().getValue(), 0.001);
        assertEquals("celsius", commonSupplyTemperature.get().getValue().getUnit().getName());
        assertEquals("connected", commonSupplyTemperature.get().getStatus().getName());

        Optional<NumericSensorFeature> burnerModulation = features.stream()
                .filter(f -> f.getName().equals("heating.burners.0.modulation"))
                .map(NumericSensorFeature.class::cast)
                .findFirst();
        assertTrue(burnerModulation.isPresent());
        assertEquals(0, burnerModulation.get().getValue().getValue(), 0.001);
        assertEquals("percent", burnerModulation.get().getValue().getUnit().getName());

        Optional<StatusSensorFeature> pumpStatus = features.stream()
                .filter(f -> f.getName().equals("heating.circuits.0.circulation.pump"))
                .map(StatusSensorFeature.class::cast)
                .findFirst();
        assertTrue(pumpStatus.isPresent());
        assertEquals("off", pumpStatus.get().getStatus().getName());

        Optional<StatisticsFeature> burnerStats = features.stream()
                .filter(f -> f.getName().equals("heating.burners.0.statistics"))
                .map(StatisticsFeature.class::cast)
                .findFirst();
        assertTrue(burnerStats.isPresent());
        assertEquals("hour", burnerStats.get().getStatistics().get("hours").getUnit().getName());
        assertEquals(5, burnerStats.get().getStatistics().get("hours").getValue());
        assertEquals("", burnerStats.get().getStatistics().get("starts").getUnit().getName());
        assertEquals(312, burnerStats.get().getStatistics().get("starts").getValue());

        Optional<ConsumptionFeature> dhwConsumption = features.stream()
                .filter(f -> f.getName().equals("heating.power.consumption.summary.dhw"))
                .map(ConsumptionFeature.class::cast)
                .findFirst();
        assertTrue(dhwConsumption.isPresent());
        assertEquals("kilowattHour", dhwConsumption.get().getToday().getUnit().getName());
        assertEquals(0, dhwConsumption.get().getToday().getValue());
        assertEquals("kilowattHour", dhwConsumption.get().getSevenDays().getUnit().getName());
        assertEquals(0.2, dhwConsumption.get().getSevenDays().getValue(), 0.001);
        assertEquals("kilowattHour", dhwConsumption.get().getMonth().getUnit().getName());
        assertEquals(0.2, dhwConsumption.get().getMonth().getValue(), 0.001);
        assertEquals("kilowattHour", dhwConsumption.get().getYear().getUnit().getName());
        assertEquals(0.9, dhwConsumption.get().getYear().getValue(), 0.001);

    }
}
