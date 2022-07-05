package com.qubular.vicare.test;

import com.qubular.vicare.ChallengeStore;
import com.qubular.vicare.TokenStore;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.URIHelper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.*;

public class VicareServiceTest {

    private BundleContext bundleContext;
    private HttpClient httpClient;
    private VicareService vicareService;

    private HttpService httpService;

    private Servlet servlet;

    private <T> T getService(Class<T> clazz) {
        return bundleContext.getService(bundleContext.getServiceReference(clazz));
    }

    @BeforeEach
    public void setUp() throws Exception {
        bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        httpClient = new HttpClient();
        httpClient.start();
        httpService = getService(HttpService.class);

        Hashtable<String, Object> newConfig = new Hashtable<>();
        String clientId = ofNullable(System.getProperty("com.qubular.vicare.tester.clientId")).orElse("myClientId");
        String accessServerUri = ofNullable(System.getProperty("com.qubular.vicare.tester.accessServerUri")).orElse("http://localhost:9000/grantAccess");
        newConfig.put(VicareService.CONFIG_CLIENT_ID, clientId);
        newConfig.put(VicareService.CONFIG_ACCESS_SERVER_URI, accessServerUri);
        getService(ConfigurationAdmin.class)
                .getConfiguration(VicareService.CONFIG_PID)
                .update(newConfig);

        vicareService = getService(VicareService.class);
    }

    boolean realConnection() {
        return Boolean.getBoolean("com.qubular.vicare.tester.realConnection");
    }

    @AfterEach
    public void tearDown() throws Exception {
        httpClient.stop();
        if (servlet != null) {
            httpService.unregister("/grantAccess");
            servlet = null;
        }
    }

    @Test
    @DisabledIf("realConnection")
    public void setupPageRendersAndIncludesRedirectURI() throws Exception {
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
        httpService.registerServlet("/grantAccess", new SimpleAccessServer(
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
        ), new Hashtable<>(), httpService.createDefaultHttpContext());

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
}
