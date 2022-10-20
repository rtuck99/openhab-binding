package com.qubular.glowmarkt.test;

import com.google.gson.GsonBuilder;
import com.qubular.glowmarkt.*;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.qubular.glowmarkt.GlowmarktSettings.DEFAULT_APPLICATION_ID;
import static org.junit.jupiter.api.Assertions.*;

public class GlowmarktServiceTest {
    private BundleContext bundleContext;
    private HttpService httpService;

    GlowmarktService glowmarktService;

    List<String> servlets = new ArrayList<>();

    private <T> T getService(Class<T> clazz) {
        return bundleContext.getService(bundleContext.getServiceReference(clazz));
    }

    @BeforeEach
    public void setUp() {
        bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        httpService = getService(HttpService.class);
        glowmarktService = getService(GlowmarktService.class);
    }

    private void registerServlet(String alias, HttpServlet servlet) throws ServletException, NamespaceException {
        servlets.add(alias);
        httpService.registerServlet(alias, servlet, new Hashtable<>(), httpService.createDefaultHttpContext());
    }

    @AfterEach
    public void tearDown() {
        servlets.forEach(httpService::unregister);
        servlets.clear();
    }

    private GlowmarktSettings glowmarktLocalTestServer() {
        return new GlowmarktSettings() {
            @Override
            public String getApplicationId() {
                return DEFAULT_APPLICATION_ID;
            }

            @Override
            public URI getApiEndpoint() {
                return URI.create("http://localhost:9000/");
            }

            @Override
            public HttpClient getHttpClient() {
                HttpClient httpClient = new HttpClient();
                try {
                    httpClient.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return httpClient;
            }
        };
    }

    static class AuthenticateRequest {
        String username;
        String password;
    }

    @Test
    public void authenticateWithGlowmarkt() throws ServletException, NamespaceException, AuthenticationFailedException, IOException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> result = new CompletableFuture<>();

        registerServlet("/auth", new HttpServlet(){
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("application/json", req.getHeader("Content-Type"));
                    assertEquals(DEFAULT_APPLICATION_ID, req.getHeader("applicationId"));
                    try (var is = req.getInputStream();
                         var isr = new InputStreamReader(is, StandardCharsets.UTF_8)){
                        AuthenticateRequest authenticateRequest = new GsonBuilder().create().fromJson(isr, AuthenticateRequest.class);
                        assertEquals("testUser", authenticateRequest.username);
                        assertEquals("testPassword", authenticateRequest.password);
                    }
                    resp.setStatus(200);
                    try (ServletOutputStream outputStream = resp.getOutputStream();
                         InputStream inputStream = getClass().getResourceAsStream("authenticationResponse.json")) {
                            outputStream.write(inputStream.readAllBytes());
                    }
                    result.complete(200);
                } catch (AssertionFailedError e) {
                    resp.setStatus(400);
                    result.completeExceptionally(e);
                }
            }
        });

        glowmarktService.authenticate(glowmarktLocalTestServer(), "testUser", "testPassword");

        assertEquals(200, result.get(3, TimeUnit.SECONDS));
    }

    @Test
    public void getVirtualEntities() throws ExecutionException, InterruptedException, TimeoutException, ServletException, NamespaceException {
        CompletableFuture<Integer> result = new CompletableFuture<>();

        registerServlet("/virtualentity", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("testToken", req.getHeader("token"));
                    assertEquals(DEFAULT_APPLICATION_ID, req.getHeader("applicationId"));
                    resp.setStatus(200);
                    try (var os = resp.getOutputStream();
                         var is = getClass().getResourceAsStream("virtualEntityResponse.json")) {
                        os.write(is.readAllBytes());
                    }
                    result.complete(200);
                } catch (AssertionFailedError e) {
                    resp.setStatus(401);
                    result.completeExceptionally(e);
                }
            }
        });

        GlowmarktSession session = new GlowmarktSession(Instant.now().plus(1, ChronoUnit.DAYS),
                "testToken");

        CompletableFuture<List<VirtualEntity>> serviceCall = CompletableFuture.supplyAsync(() -> {
            try {
                return glowmarktService.getVirtualEntities(session, glowmarktLocalTestServer());
            } catch (IOException | AuthenticationFailedException e) {
                throw new RuntimeException(e);
            }
        });
        CompletableFuture.allOf(result, serviceCall).get(3, TimeUnit.SECONDS);
        assertEquals(200, result.get());
        List<VirtualEntity> virtualEntities = serviceCall.get();
        assertEquals(2, virtualEntities.size());
        assertEquals("Smart Home 1", virtualEntities.get(0).getName());
        assertEquals("dc9069a7-7695-43fd-8f27-16b1c94213da", virtualEntities.get(0).getVirtualEntityId());
        assertEquals("cc90b599-2705-4b13-98d4-3306f81169cf", virtualEntities.get(0).getVirtualEntityTypeId());
        assertEquals(4, virtualEntities.get(0).getResources().size());
        assertEquals("73f70bcd-3743-4009-a2c4-e98cc959c030", virtualEntities.get(0).getResources().get(0).getResourceId());
        assertEquals("b120977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntities.get(0).getResources().get(1).getResourceId());
        assertEquals("b320977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntities.get(0).getResources().get(2).getResourceId());
        assertEquals("b420977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntities.get(0).getResources().get(3).getResourceId());
        assertEquals("Smart Business 2", virtualEntities.get(1).getName());
        assertEquals("dc9069a7-7695-43fd-8f27-16b1c94213da", virtualEntities.get(1).getVirtualEntityId());
        assertEquals("cc90b599-2705-4b13-98d4-3306f81169cf", virtualEntities.get(1).getVirtualEntityTypeId());
        assertEquals(4, virtualEntities.get(1).getResources().size());
        assertEquals("74f70bcd-3743-4009-a2c4-e98cc959c030", virtualEntities.get(1).getResources().get(0).getResourceId());
        assertEquals("c120977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntities.get(1).getResources().get(1).getResourceId());
        assertEquals("c320977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntities.get(1).getResources().get(2).getResourceId());
        assertEquals("c420977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntities.get(1).getResources().get(3).getResourceId());
    }

    @Test
    public void getVirtualEntityResources() throws ServletException, NamespaceException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> result = new CompletableFuture<>();

        registerServlet("/virtualentity/dc9069a7-7695-43fd-8f27-16b1c94213da/resources", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("testToken", req.getHeader("token"));
                    assertEquals(DEFAULT_APPLICATION_ID, req.getHeader("applicationId"));
                    resp.setStatus(200);
                    try (var os = resp.getOutputStream();
                         var is = getClass().getResourceAsStream("virtualEntityResources.json")) {
                        os.write(is.readAllBytes());
                    }
                    result.complete(200);
                } catch (AssertionFailedError e) {
                    resp.setStatus(401);
                    result.completeExceptionally(e);
                }
            }
        });

        GlowmarktSession session = new GlowmarktSession(Instant.now().plus(1, ChronoUnit.DAYS),
                "testToken");

        CompletableFuture<VirtualEntity> serviceCall = CompletableFuture.supplyAsync(() -> {
            try {
                return glowmarktService.getVirtualEntity(session, glowmarktLocalTestServer(), "dc9069a7-7695-43fd-8f27-16b1c94213da");
            } catch (IOException | AuthenticationFailedException e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(result, serviceCall).get(3, TimeUnit.SECONDS);
        assertEquals(200, result.get());
        VirtualEntity virtualEntity = serviceCall.get();

        assertEquals("dc9069a7-7695-43fd-8f27-16b1c94213da", virtualEntity.getVirtualEntityId());
        assertEquals("cc90b599-2705-4b13-98d4-3306f81169cf", virtualEntity.getVirtualEntityTypeId());
        assertEquals("Smart Home 1", virtualEntity.getName());
        assertEquals(4, virtualEntity.getResources().size());
        assertEquals("73f70bcd-3743-4009-a2c4-e98cc959c030", virtualEntity.getResources().get(0).getResourceId());
        assertEquals("electricity", virtualEntity.getResources().get(0).getName());
        assertEquals("electricity.consumption", virtualEntity.getResources().get(0).getClassifier());
        assertEquals("electricity consumption", virtualEntity.getResources().get(0).getDescription());
        assertEquals("kWh", virtualEntity.getResources().get(0).getBaseUnit());
        assertEquals("b120977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntity.getResources().get(1).getResourceId());
        assertEquals("electricity cost", virtualEntity.getResources().get(1).getName());
        assertEquals("electricity.consumption.cost", virtualEntity.getResources().get(1).getClassifier());
        assertEquals("electricity cost", virtualEntity.getResources().get(1).getDescription());
        assertEquals("pence", virtualEntity.getResources().get(1).getBaseUnit());
        assertEquals("b320977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntity.getResources().get(2).getResourceId());
        assertEquals("gas", virtualEntity.getResources().get(2).getName());
        assertEquals("gas.consumption", virtualEntity.getResources().get(2).getClassifier());
        assertEquals("gas consumption", virtualEntity.getResources().get(2).getDescription());
        assertEquals("kWh", virtualEntity.getResources().get(2).getBaseUnit());
        assertEquals("b420977-aeb6-4b56-a0d3-d4a9b485848a", virtualEntity.getResources().get(3).getResourceId());
        assertEquals("gas cost", virtualEntity.getResources().get(3).getName());
        assertEquals("gas.consumption.cost", virtualEntity.getResources().get(3).getClassifier());
        assertEquals("gas cost", virtualEntity.getResources().get(3).getDescription());
        assertEquals("pence", virtualEntity.getResources().get(3).getBaseUnit());
    }

    @Test
    public void getResourceReadings() throws ServletException, NamespaceException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> result = new CompletableFuture<>();

        registerServlet("/resource/73f70bcd-3743-4009-a2c4-e98cc959c030/readings", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("testToken", req.getHeader("token"));
                    assertEquals(DEFAULT_APPLICATION_ID, req.getHeader("applicationId"));
                    assertEquals("2018-04-10T00:00:00", req.getParameter("from"));
                    assertEquals("2018-04-23T23:59:59", req.getParameter("to"));
                    assertEquals("P1D", req.getParameter("period"));
                    assertEquals("0", req.getParameter("offset"));
                    assertEquals("sum", req.getParameter("function"));
                    resp.setStatus(200);
                    try (var os = resp.getOutputStream();
                         var is = getClass().getResourceAsStream("resourceReadings.json")) {
                        os.write(is.readAllBytes());
                    }
                    result.complete(200);
                } catch (AssertionFailedError e) {
                    resp.setStatus(401);
                    result.completeExceptionally(e);
                }
            }
        });

        GlowmarktSession session = new GlowmarktSession(Instant.now().plus(1, ChronoUnit.DAYS),
                "testToken");

        CompletableFuture<List<ResourceData>> serviceCall = CompletableFuture.supplyAsync(() -> {
            try {
                return glowmarktService.getResourceReadings(session,
                        glowmarktLocalTestServer(),
                        "73f70bcd-3743-4009-a2c4-e98cc959c030",
                        LocalDateTime.of(2018, 4, 10, 0, 0, 0).toInstant(ZoneOffset.UTC),
                        LocalDateTime.of(2018, 4, 23, 23, 59, 59).toInstant(ZoneOffset.UTC),
                        AggregationPeriod.P1D,
                        AggregationFunction.SUM);
            } catch (IOException | AuthenticationFailedException e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(result, serviceCall).get(3, TimeUnit.SECONDS);
        assertEquals(200, result.get());
        List<ResourceData> resourceData = serviceCall.get();

        assertEquals(2, resourceData.size());
        assertEquals(LocalDateTime.of(2018, 4, 10, 0, 0, 0).toInstant(ZoneOffset.UTC), resourceData.get(0).getTimestamp());
        assertEquals(48.79, resourceData.get(0).getReading(), 1e-6);
        assertEquals(LocalDateTime.of(2018, 4, 11, 0, 0, 0).toInstant(ZoneOffset.UTC), resourceData.get(1).getTimestamp());
        assertEquals(48.826, resourceData.get(1).getReading(), 1e-6);
    }

    @Test
    public void tokenExpiryTriggersThrowsAuthenticationFailed() throws ServletException, NamespaceException {
        CompletableFuture<Integer> result = new CompletableFuture<>();

        registerServlet("/resource/73f70bcd-3743-4009-a2c4-e98cc959c030/readings", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    fail("This call should not be made");
                } catch (AssertionFailedError e) {
                    resp.setStatus(401);
                    result.completeExceptionally(e);
                }
            }
        });

        GlowmarktSession session = new GlowmarktSession(Instant.now().minus(1, ChronoUnit.DAYS),
                "testToken");

        assertThrows(AuthenticationFailedException.class, () -> glowmarktService.getResourceReadings(session,
                glowmarktLocalTestServer(),
                "73f70bcd-3743-4009-a2c4-e98cc959c030",
                LocalDateTime.of(2018, 4, 10, 0, 0, 0).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(2018, 4, 23, 23, 59, 59).toInstant(ZoneOffset.UTC),
                AggregationPeriod.P1D,
                AggregationFunction.SUM));

        assertFalse(result.isDone());
    }

    @Test
    public void getFirstTimeReturnsInstant() throws ServletException, NamespaceException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        registerServlet("/resource/8f2a0722-3b59-47a6-a115-d43fcc0c5d1c/first-time", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("testToken", req.getHeader("token"));
                    assertEquals(DEFAULT_APPLICATION_ID, req.getHeader("applicationId"));
                    resp.setStatus(200);
                    try (var os = resp.getOutputStream();
                         var is = getClass().getResourceAsStream("firstTimeResponse.json")) {
                        os.write(is.readAllBytes());
                    }
                    result.complete(200);
                } catch (AssertionFailedError e) {
                    resp.setStatus(401);
                    result.completeExceptionally(e);
                }
            }
        });

        GlowmarktSession session = new GlowmarktSession(Instant.now().plus(1, ChronoUnit.DAYS),
                "testToken");

        CompletableFuture<Instant> serviceCall = CompletableFuture.supplyAsync(() -> {
            try {
                return glowmarktService.getFirstTime(session, glowmarktLocalTestServer(),
                        "8f2a0722-3b59-47a6-a115-d43fcc0c5d1c");
            } catch (AuthenticationFailedException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(result, serviceCall).get(3, TimeUnit.SECONDS);
        assertEquals(200, result.get());
        assertEquals(Instant.parse("2011-06-18T12:43:00Z"), serviceCall.get());
    }

    @Test
    public void getLastTimeReturnsInstant() throws ServletException, NamespaceException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        registerServlet("/resource/8f2a0722-3b59-47a6-a115-d43fcc0c5d1c/last-time", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("testToken", req.getHeader("token"));
                    assertEquals(DEFAULT_APPLICATION_ID, req.getHeader("applicationId"));
                    resp.setStatus(200);
                    try (var os = resp.getOutputStream();
                         var is = getClass().getResourceAsStream("lastTimeResponse.json")) {
                        os.write(is.readAllBytes());
                    }
                    result.complete(200);
                } catch (AssertionFailedError e) {
                    resp.setStatus(401);
                    result.completeExceptionally(e);
                }
            }
        });

        GlowmarktSession session = new GlowmarktSession(Instant.now().plus(1, ChronoUnit.DAYS),
                "testToken");

        CompletableFuture<Instant> serviceCall = CompletableFuture.supplyAsync(() -> {
            try {
                return glowmarktService.getLastTime(session, glowmarktLocalTestServer(),
                        "8f2a0722-3b59-47a6-a115-d43fcc0c5d1c");
            } catch (AuthenticationFailedException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(result, serviceCall).get(3, TimeUnit.SECONDS);
        assertEquals(200, result.get());
        assertEquals(Instant.parse("2021-02-12T10:05:02Z"), serviceCall.get());
    }

    @Test
    public void getResourceTariffFetchesTariffData() throws ServletException, NamespaceException, AuthenticationFailedException, IOException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Integer> result = tariffEndpoint();
        GlowmarktSession session = new GlowmarktSession(Instant.now().plus(1, ChronoUnit.DAYS),
                                                        "testToken");
        TariffResponse tariffResponse = glowmarktService.getResourceTariff(session, glowmarktLocalTestServer(),
                                                                         "b2e6fa7a-ac81-41a1-825a-22d9038c671b");
        List<TariffData> tariffData = tariffResponse.getData();
        assertEquals(200, result.completeOnTimeout(0, 1, TimeUnit.SECONDS).get());
        assertEquals("electricity consumption", tariffResponse.getName());
        TariffStructure tariffStructure = tariffData.get(0).getStructure().get(0);
        assertEquals(1, tariffData.size());
        assertEquals(LocalDateTime.parse("2022-10-02T00:00:00"), tariffData.get(0).getFrom());
        assertEquals(1, tariffData.get(0).getStructure().size());
        assertEquals("1", tariffStructure.getWeekName());
        assertEquals("DCC", tariffStructure.getSource());
        assertEquals(2, tariffStructure.getPlanDetails().size());
        assertEquals(new BigDecimal("44.4"), ((StandingChargeTariffPlanDetail) tariffStructure.getPlanDetails().get(0)).getStanding());
        assertEquals(1, ((PerUnitTariffPlanDetail) tariffStructure.getPlanDetails().get(1)).getTier());
        assertEquals(new BigDecimal("34.22"),
                     ((PerUnitTariffPlanDetail) tariffStructure.getPlanDetails().get(1)).getRate());
    }

    private CompletableFuture<Integer> tariffEndpoint() throws ServletException, NamespaceException {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        registerServlet("/resource/b2e6fa7a-ac81-41a1-825a-22d9038c671b/tariff", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try {
                    assertEquals("testToken", req.getHeader("token"));
                    assertEquals(DEFAULT_APPLICATION_ID, req.getHeader("applicationId"));
                    resp.setStatus(200);
                    try (var os = resp.getOutputStream();
                         var is = getClass().getResourceAsStream("tariffResponse.json")) {
                        os.write(is.readAllBytes());
                    }
                    result.complete(200);
                } catch (AssertionFailedError e) {
                    resp.setStatus(401);
                    result.completeExceptionally(e);
                }
            }
        });
        return result;
    }
}
