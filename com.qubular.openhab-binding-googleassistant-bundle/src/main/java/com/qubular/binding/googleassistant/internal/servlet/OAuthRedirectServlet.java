package com.qubular.binding.googleassistant.internal.servlet;

import com.qubular.binding.googleassistant.internal.RestDeviceRegistrationService;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jetty.client.HttpClient;
import com.qubular.binding.googleassistant.internal.DeviceRegistrationService;
import com.qubular.binding.googleassistant.internal.OAuthService;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantBindingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OAuthRedirectServlet extends HttpServlet {
    public static final String CONTEXT_PATH = "/googleAssistant";
    private static Logger logger = LoggerFactory.getLogger(OAuthRedirectServlet.class);

    private OAuthService.ClientCredentials credentials;
    private final OAuthService oAuthService;
    private final GoogleAssistantBindingConfig bindingConfig;

    private final HttpClient httpClient;

    public OAuthRedirectServlet(OAuthService oAuthService, GoogleAssistantBindingConfig bindingConfig, HttpClient httpClient) {
        logger.info("Starting OAuthRedirectServlet");
        this.oAuthService = oAuthService;
        this.bindingConfig = bindingConfig;
        this.httpClient = httpClient;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("Fetching {}", req.getRequestURL());
        try {
            URI uri = URI.create((@NonNull String) req.getRequestURI());
            String relPath = uri.getPath().replaceFirst(CONTEXT_PATH, "");
            switch (relPath) {
                case "/doAuthorisation":
                    renderLandingPage(req, resp);
                    break;
                case "/authCode":
                    extractAuthCode(req, resp);
                    break;
                case "/deviceRegistration":
                    renderDeviceRegistration(req, resp);
                    break;
                default:
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    break;
            }
        } catch (RuntimeException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            logger.warn("Unexpected exception caught processing request", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            URI uri = URI.create((@NonNull String) req.getRequestURL().toString());
            String relPath = uri.getPath().replaceFirst(CONTEXT_PATH, "");
            switch (relPath) {
                case "/deviceModels":
                    addDeviceModel(req, resp);
                    break;
                case "/devices":
                    addDevice(req, resp);
                    break;
                default:
                    resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    break;
            }
        } catch (RuntimeException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            logger.warn("Unexpected exception caught processing request", e);
        }
    }

    private void addDevice(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OAuthService.OAuthSession session = checkValidOAuthSession(req, resp);
        if (session == null) {
            return;
        }

        try {
            Map<String, String> urlData = parseUrlEncodedContent(req);

            DeviceRegistrationService.Device device = new DeviceRegistrationService.Device();
            device.id = urlData.get("deviceId");
            device.modelId = urlData.get("modelId");
            device.nickname = urlData.get("nickname");
            device.clientType = DeviceRegistrationService.ClientType.valueOf(urlData.get("clientType"));

            getRestDeviceRegistrationService(session).addDevice(device);
        } catch (IOException e) {
            logger.warn("Unable to decode form data", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } catch (DeviceRegistrationService.DeviceRegistrationException e) {
            logger.warn("Unable to add device instance", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        renderDeviceRegistration(req, resp, session);
    }

    private void addDeviceModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OAuthService.OAuthSession session = checkValidOAuthSession(req, resp);
        if (session == null) {
            return;
        }

        try {
            Map<String, String> urlData = parseUrlEncodedContent(req);

            DeviceRegistrationService.DeviceModel deviceModel = new DeviceRegistrationService.DeviceModel();
            deviceModel.deviceModelId = urlData.get("deviceModelId");
            deviceModel.deviceType = urlData.get("deviceType");
            deviceModel.projectId = credentials.projectId;
            deviceModel.manifest = new DeviceRegistrationService.Manifest();
            deviceModel.manifest.manufacturer = "OpenHAB";
            deviceModel.manifest.productName = "OpenHAB-" + deviceModel.deviceType;
            deviceModel.manifest.deviceDescription = urlData.get("deviceDescription");

            getRestDeviceRegistrationService(session).addDeviceModel(deviceModel);
        } catch (IOException e) {
            logger.warn("Unable to decode form data", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } catch (DeviceRegistrationService.DeviceRegistrationException e) {
            logger.warn("Unable to add device model", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        renderDeviceRegistration(req, resp, session);
    }

    private Map<String, String> parseUrlEncodedContent(HttpServletRequest req) throws IOException {
        Pattern nvPairPattern = Pattern.compile("(.*?)=(.*)");
        String formData = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> urlData = Arrays.stream(formData.split("&"))
                .map(nvPair -> nvPairPattern.matcher(nvPair))
                .filter(Matcher::matches)
                .collect(Collectors.toMap(
                        matcher -> URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8),
                        matcher -> URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8)
                ));
        return urlData;
    }

    private void extractAuthCode(HttpServletRequest req, HttpServletResponse resp) {
        if (!HttpMethod.GET.equals(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String error = req.getParameter("error");
        if (error != null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            generateErrorPage(resp, "Authentication returned error: " + error);
        } else {
            String authCode = req.getParameter("code");
            if (authCode != null) {
                try {
                    OAuthService.OAuthSession googleOAuthSession = oAuthService.accessTokenFromAuthz(RedirectURLHelper.getRedirectUri(req), authCode).join();
                    logger.debug("Got oauth result with refresh token {}", googleOAuthSession.getRefreshToken());
                    googleOAuthSession.saveToSession(req.getSession());
                } catch (CompletionException e) {
                    logger.warn("Unable to fetch access token", e);
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    generateErrorPage(resp, "Unable to fetch access token: " + e.getMessage());
                }
            }
        }
    }

    public void setCredentials(OAuthService.ClientCredentials credentials) {
        this.credentials = credentials;
        credentials.scopes = List.of("https://www.googleapis.com/auth/assistant-sdk-prototype");
    }

    private void generateErrorPage(HttpServletResponse resp, String message) {

    }

    private void renderLandingPage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!HttpMethod.GET.equals(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        logger.debug("Generating landing page");
        URI authorisationUri = oAuthService.getAuthorisationUri(OAuthService.ACCESS_TYPE_OFFLINE,
                RedirectURLHelper.getRedirectUri(req));
        resp.setStatus(HttpServletResponse.SC_FOUND);
        resp.setHeader(HttpHeaders.LOCATION, authorisationUri.toString());
        resp.getOutputStream().close();
    }

    private void renderDeviceRegistration(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!HttpMethod.GET.equals(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        OAuthService.OAuthSession session = checkValidOAuthSession(req, resp);
        renderDeviceRegistration(req, resp, session);
    }

    private void renderDeviceRegistration(HttpServletRequest req, HttpServletResponse resp, OAuthService.OAuthSession session) throws IOException {
        if (session == null) return;

        logger.debug("Generating device registration page");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = getClass().getResourceAsStream("deviceRegistration.html")) {
            String htmlTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // TODO XSS escape these strings
            RestDeviceRegistrationService googleAssistantService = getRestDeviceRegistrationService(session);
            String deviceModelRows = googleAssistantService.getDeviceModels().stream()
                    .map(deviceModel -> String.format("<tr><td>%s</td><td>%s</td><td>%s</td></tr>",
                            deviceModel.deviceModelId,
                            deviceModel.name,
                            deviceModel.deviceType))
                    .collect(Collectors.joining());
            htmlTemplate = htmlTemplate.replaceAll("\\$\\{registeredDevices\\}",
                    "<table><tr><th>Model ID</th><th>Name</th><th>Device Type</th></tr>" + deviceModelRows + "</table>");

            htmlTemplate = htmlTemplate.replaceAll("\\$\\{redirectUri\\}", getDoAuthorisationUri(req));

            // TODO XSS escape these strings
            String deviceInstanceRows = googleAssistantService.getDeviceInstances().stream()
                            .map(device -> String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                                    device.id,
                                    device.modelId,
                                    device.clientType,
                                    device.nickname))
                                    .collect(Collectors.joining());
            htmlTemplate = htmlTemplate.replaceAll("\\$\\{registeredDeviceInstances\\}",
                    "<table><tr><th>Device ID</th><th>Model ID</th><th>Client Type</th><th>nickname</th></tr>" +
                    deviceInstanceRows +
                    "</table>");
            resp.getOutputStream().println(htmlTemplate);
            resp.setStatus(HttpServletResponse.SC_OK);
        } catch (DeviceRegistrationService.DeviceRegistrationException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            logger.warn("Unexpected problem fetching device registrations.", e);
            return;
        }
    }

    private String getDoAuthorisationUri(HttpServletRequest req) {
        return URI.create((@NonNull String) req.getRequestURL().toString()).resolve("./doAuthorisation").toString();
    }

    private RestDeviceRegistrationService getRestDeviceRegistrationService(OAuthService.OAuthSession session) {
        RestDeviceRegistrationService googleAssistantService = new RestDeviceRegistrationService(
                httpClient,
                oAuthService,
                credentials,
                session);
        return googleAssistantService;
    }

    private OAuthService.OAuthSession checkValidOAuthSession(HttpServletRequest req, HttpServletResponse resp) {
        OAuthService.OAuthSession session = OAuthService.OAuthSession.loadFromSession(req.getSession());
        if (session == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }
        return session;
    }
}
