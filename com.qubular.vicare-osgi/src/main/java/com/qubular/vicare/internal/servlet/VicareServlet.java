package com.qubular.vicare.internal.servlet;

import com.qubular.vicare.ChallengeStore;
import com.qubular.vicare.HttpClientProvider;
import com.qubular.vicare.URIHelper;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static javax.servlet.http.HttpServletResponse.SC_OK;

public class VicareServlet extends HttpServlet {
    private static final Pattern AUTH_CODE_PATTERN = Pattern.compile("/authCode/(.*)");
    private ChallengeStore<?> challengeStore;
    private final URI accessServerUri;
    private final HttpClientProvider httpClientProvider;
    private final String clientId;
    public static final String CONTEXT_PATH = "/vicare";

    public static final URI AUTHORISE_ENDPOINT = URI.create("https://iam.viessmann.com/idp/v2/authorize");
    private static final Logger logger = LoggerFactory.getLogger(VicareServlet.class);

    public VicareServlet(ChallengeStore<?> challengeStore,
                         URI accessServerUri,
                         HttpClientProvider httpClientProvider,
                         String clientId) {
        this.challengeStore = challengeStore;
        this.accessServerUri = accessServerUri;
        this.httpClientProvider = httpClientProvider;
        this.clientId = clientId;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("Fetching {}", req.getRequestURL());

        URI uri = URI.create(req.getRequestURI());
        String relPath = uri.getPath().replaceFirst(CONTEXT_PATH, "");

        Matcher matcher = AUTH_CODE_PATTERN.matcher(relPath);
        if (matcher.matches()) {
            extractAuthCodeAndFetchAccessToken(req, resp, matcher.group(1));
        } else {
            switch (relPath) {
                case "/setup":
                    renderSetupPage(req, resp);
                    break;
                default:
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    private URI getRedirectURI(HttpServletRequest req, String challengeKey) {
        URI redirectUriRoot = RedirectURLHelper.getRedirectUri(req);
        return redirectUriRoot.resolve(redirectUriRoot.getPath() + "/" + challengeKey);
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
                                Map<String, String> tokenRequestQueryParams = Map.of("client_id", clientId,
                                        "redirect_uri", RedirectURLHelper.getNavigatedURL(req).toString(),
                                        "grant_type", "authorization_code",
                                        "code_verifier", challenge.getChallengeCode(),
                                        "code", code);
                                String content = URIHelper.generateQueryParams(tokenRequestQueryParams);
                                ContentResponse accessTokenResponse = httpClientProvider.getHttpClient()
                                        .POST(accessServerUri)
                                        .header("Content-Type", "application/x-www-form-urlencoded")
                                        .content(new StringContentProvider(content, StandardCharsets.UTF_8))
                                        .send();

                                if (accessTokenResponse.getStatus() != SC_OK) {
                                    logger.warn("Access server returned status {}", accessTokenResponse.getStatus());
                                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                } else {
                                    // TODO
                                }
                            } catch (InterruptedException | ExecutionException | TimeoutException | URISyntaxException e) {
                                logger.warn("Unable to fetch access token", e);
                                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            }
                        },
                        () -> resp.setStatus(HttpServletResponse.SC_NOT_FOUND)
                );
    }

    private void renderSetupPage(HttpServletRequest req, HttpServletResponse resp) {
        try (InputStream is = getClass().getResourceAsStream("setup.html")) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            ChallengeStore.Challenge challenge = challengeStore.createChallenge();
            html = html.replaceAll("\\$\\{redirectUri\\}", getRedirectURI(req, challenge.getKey()).toString());
            html = html.replaceAll("\\$\\{authServerUri\\}", authoriseEndpointUri(req, challenge));

            // TODO substitute authorised status

            resp.setContentType("text/html");
            try (ServletOutputStream os = resp.getOutputStream()) {
                os.print(html);
            }
        } catch (IOException e) {
            logger.warn("Unable to render setup page", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String authoriseEndpointUri(HttpServletRequest req, ChallengeStore.Challenge challenge) {
        Map<String, String> queryParams = Map.of("redirect_uri", getRedirectURI(req, challenge.getKey()).toString(),
                "response_type", "code",
                "scope", "IoT User offline_access",
                "code_challenge_method", "plain",
                "code_challenge", challenge.getChallengeCode());
        return AUTHORISE_ENDPOINT.toString() + "?" +
                queryParams.entrySet().stream()
                        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
    }
}
