package com.qubular.vicare.internal.servlet;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qubular.vicare.*;
import com.qubular.vicare.internal.oauth.AccessGrantResponse;
import com.qubular.vicare.internal.oauth.ErrorResponse;
import com.qubular.vicare.model.Device;
import com.qubular.vicare.model.Gateway;
import com.qubular.vicare.model.Installation;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

public class VicareServlet extends HttpServlet {
    private static final String SESSION_ATTR_ACCESS_TOKEN = "accessToken";
    private final VicareConfiguration config;
    private ChallengeStore<?> challengeStore;
    private final TokenStore tokenStore;
    private final HttpClientProvider httpClientProvider;
    private final VicareService vicareService;
    public static final String CONTEXT_PATH = "/vicare";

    public static final URI AUTHORISE_ENDPOINT = URI.create("https://iam.viessmann.com/idp/v2/authorize");
    private static final Logger logger = LoggerFactory.getLogger(VicareServlet.class);


    public VicareServlet(VicareService vicareService,
                         ChallengeStore<?> challengeStore,
                         TokenStore tokenStore,
                         HttpClientProvider httpClientProvider,
                         VicareConfiguration config) {
        this.config = config;
        logger.info("Configuring servlet with accessServerUri {}", config.getAccessServerURI());
        this.vicareService = vicareService;
        this.challengeStore = challengeStore;
        this.tokenStore = tokenStore;
        this.httpClientProvider = httpClientProvider;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("Fetching {}", req.getRequestURL());

        URI uri = URI.create(req.getRequestURI());
        String relPath = uri.getPath().replaceFirst(CONTEXT_PATH, "");

        switch (relPath) {
            case "/authCode":
                extractAuthCodeAndFetchAccessToken(req, resp, req.getParameter("state"));
                break;
            case "/setup":
                renderSetupPage(req, resp);
                break;
            case "/redirect":
                redirectToAuthServer(req, resp);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void redirectToAuthServer(HttpServletRequest req, HttpServletResponse resp) {
        ChallengeStore.Challenge challenge = challengeStore.createChallenge();
        try {
            String redirectUri = authoriseEndpointUri(req, challenge);
            logger.debug("Redirecting browser to {}", redirectUri);
            resp.sendRedirect(redirectUri);
        } catch (IOException e) {
            logger.warn("Unable to generate redirect uri", e);
            resp.setStatus(SC_INTERNAL_SERVER_ERROR);
        }
    }

    private URI getRedirectURI(HttpServletRequest req) {
        URI redirectUriRoot = RedirectURLHelper.getRedirectUri(req);
        return redirectUriRoot;
    }

    private void extractAuthCodeAndFetchAccessToken(HttpServletRequest req, HttpServletResponse resp, String challengeKey) {
        String code = req.getParameter("code");
        if (code == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        challengeStore.getChallenge(challengeKey)
                .ifPresentOrElse(challenge -> {
                            try {
                                Fields fields = new Fields();
                                fields.put("grant_type", "authorization_code");
                                fields.put("code_verifier", challenge.getChallengeCode());
                                fields.put("client_id", config.getClientId());
                                fields.put("redirect_uri", RedirectURLHelper.getNavigatedURL(req).toString());
                                fields.put("code", code);
                                if (logger.isDebugEnabled()) {
                                    logger.debug("sending to access server {}:", config.getAccessServerURI());
                                    fields.forEach(f -> logger.debug("{}={}", f.getName(), f.getValue()));
                                }
                                ContentResponse accessTokenResponse = httpClientProvider.getHttpClient()
                                        .POST(config.getAccessServerURI())
                                        .content(new FormContentProvider(fields, StandardCharsets.UTF_8))
                                        .accept("application/json")
                                        .send();

                                if (accessTokenResponse.getStatus() != SC_OK) {
                                    logger.warn("Access server returned status {}", accessTokenResponse.getStatus());
                                    handleError(resp, accessTokenResponse);
                                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                } else {
                                    Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                                            .create();
                                    AccessGrantResponse accessGrantResponse = gson.fromJson(accessTokenResponse.getContentAsString(),
                                            AccessGrantResponse.class);
                                    logger.debug("Got access token, expiry in {}", accessGrantResponse.expiresIn);
                                    tokenStore.storeAccessToken(accessGrantResponse.accessToken, Instant.now().plusSeconds(accessGrantResponse.expiresIn));
                                    req.getSession().setAttribute(SESSION_ATTR_ACCESS_TOKEN, accessGrantResponse.accessToken);
                                    if (accessGrantResponse.refreshToken != null) {
                                        logger.debug("Got refresh token");
                                        tokenStore.storeRefreshToken(accessGrantResponse.refreshToken);
                                    }
                                }
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                logger.warn("Unable to fetch access token", e);
                                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            }
                        },
                        () -> resp.setStatus(HttpServletResponse.SC_NOT_FOUND)
                );
    }

    private void handleError(HttpServletResponse response, ContentResponse accessTokenResponse) {
        if (accessTokenResponse.getMediaType().equals("application/json")) {
            Gson gson = new GsonBuilder().create();
            ErrorResponse errorResponse = gson.fromJson(accessTokenResponse.getContentAsString(), ErrorResponse.class);
            try (PrintWriter writer = response.getWriter()) {
                writer.format("Received error from server: %s", errorResponse.error);
            } catch (IOException e) {
                logger.warn("Unable to handle error", e);
            }
        }
    }

    private void renderSetupPage(HttpServletRequest req, HttpServletResponse resp) {
        try (InputStream is = getClass().getResourceAsStream("setup.html")) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            boolean authorised = tokenStore.getAccessToken()
                    .map(t -> t.expiry)
                    .map(t -> t.isAfter(Instant.now()))
                    .orElse(false);
            html = html.replaceAll("\\$\\{redirectUri\\}", getRedirectURI(req).toString());
            html = html.replaceAll("\\$\\{authServerUri\\}", RedirectURLHelper.getNavigatedURL(req).toURI().resolve("redirect").toString());
            html = html.replaceAll("\\$\\{authorisationStatus\\}", authorised ? "AUTHORISED" : "NOT AUTHORISED");
            html = html.replaceAll("\\$\\{deviceMappings\\}", renderDeviceMappings());
            resp.setContentType("text/html");
            try (ServletOutputStream os = resp.getOutputStream()) {
                os.print(html);
            }
        } catch (IOException e) {
            logger.warn("Unable to render setup page", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (URISyntaxException e) {
            logger.warn("Unable to generate redirect.", e);
            resp.setStatus(SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String renderDeviceMappings() {
        StringBuilder builder = new StringBuilder();
        try {
            List<Installation> installations = vicareService.getInstallations();
            for (Installation installation : installations) {
                for (Gateway gateway : installation.getGateways()) {
                    for (Device device : gateway.getDevices()) {
                        builder.append(String.format("<tr><td>%d: %s</td>", installation.getId(), installation.getDescription()));
                        builder.append(String.format("<td>%s: %s</td>", escapeHtml4(gateway.getSerial()), escapeHtml4(gateway.getGatewayType())));
                        builder.append(String.format("<td>%s: %s</td></tr>", escapeHtml4(device.getId()), escapeHtml4(device.getModelId())));
                    }
                }
            }
            return builder.toString();
        } catch (AuthenticationException e) {
            return "<tr><td>OpenHAB is not authorised</td></tr>";
        } catch (IOException e) {
            return "<tr><td>Unable to fetch data: " + escapeHtml4(e.getMessage()) + "</td></tr>";
        }
    }

    private String authoriseEndpointUri(HttpServletRequest req, ChallengeStore.Challenge challenge) {
        Map<String, String> queryParams = Map.of("redirect_uri", getRedirectURI(req).toString(),
                "response_type", "code",
                "scope", "IoT User offline_access",
                "code_challenge", challenge.getChallengeCode(),
                "state", challenge.getKey(),
                "client_id", config.getClientId());


        return AUTHORISE_ENDPOINT.toString() + "?" +
                URIHelper.generateQueryParamsForURI(queryParams);
    }
}
