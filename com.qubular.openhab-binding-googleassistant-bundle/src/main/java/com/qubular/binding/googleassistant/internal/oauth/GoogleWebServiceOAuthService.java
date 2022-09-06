package com.qubular.binding.googleassistant.internal.oauth;

import com.google.common.base.Strings;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qubular.binding.googleassistant.internal.OAuthService;
import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.auth.client.oauth2.*;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component(service = OAuthService.class)
public class GoogleWebServiceOAuthService implements OAuthService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleWebServiceOAuthService.class);
    public static final String GOOGLE_BASE_AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String OAUTH_SERVICE_HANDLE = "googleAssistantOAuthClient";
    private final HttpClientFactory httpClientFactory;
    private OAuthFactory oAuthFactory;
    private ClientCredentials credentials;
    private Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private OAuthClientService oAuthService;

    @Activate
    public GoogleWebServiceOAuthService(@Reference HttpClientFactory httpClientFactory,
                                        @Reference OAuthFactory oAuthFactory) {
        this.httpClientFactory = httpClientFactory;
        this.oAuthFactory = oAuthFactory;
    }

    @Override
    public void setCredentials(ClientCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public URI getAuthorisationUri(String accessType, URI redirectUri) {
                Map<String, String> queryParams = Map.of("client_id", credentials.clientId,
                "redirect_uri", redirectUri.toString(),
                "response_type", "code",
                "scope", credentials.scopes.stream().collect(Collectors.joining(" ")),
                "access_type", accessType,
                "prompt", "consent");

        String uriString =
                queryParams.entrySet().stream()
                        .map((entry) -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                        .reduce((s1, s2) -> s1 + "&" + s2)
                        .orElse("");
        return URI.create(getAuthUri() + "?" + uriString);
    }

    @NonNull
    private String getAuthUri() {
        return credentials.authUri != null ? credentials.authUri : GOOGLE_BASE_AUTH_URI;
    }

    @Override
    public CompletableFuture<OAuthSession> accessTokenFromAuthz(URI redirectUri, String authorisation) {
        CompletableFuture<OAuthSession> result = new CompletableFuture<>();
        return result.completeAsync(() -> {
            OAuthSession session = null;
            try {
                session = new OAuthSession(getOAuthService().getAccessTokenResponseByAuthorizationCode(authorisation, redirectUri.toString()));
                logger.debug("Extracted OAuth token with refresh token '{}'", sanitize(session.getRefreshToken()));
            } catch (org.openhab.core.auth.client.oauth2.OAuthException | IOException | OAuthResponseException e) {
                result.completeExceptionally(e);
            }
            return session;
        });
    }

    private static String sanitize(String s) {
        return Strings.nullToEmpty(s).replaceAll(".", "*");
    }

    @Override
    public CompletableFuture<OAuthSession> maybeRefreshAccessToken(OAuthSession session) {
        CompletableFuture result = new CompletableFuture<>();
        return result.completeAsync(() -> {
            try {
                AccessTokenResponse accessTokenResponse = getOAuthService().getAccessTokenResponse();
                if (accessTokenResponse == null) {
                    logger.debug("Unable to refresh token, getAccessTokenResponse() returned null");
                    return session;
                }
                logger.debug("Refreshed OAuth access token with refresh token '{}'", sanitize(accessTokenResponse.getRefreshToken()));
                logger.debug("New access token expires in {} seconds", accessTokenResponse.getExpiresIn());
                return new OAuthSession(accessTokenResponse);
            } catch (org.openhab.core.auth.client.oauth2.OAuthException | IOException | OAuthResponseException e) {
                logger.warn("Unable to refresh OAuth token", e);
                result.completeExceptionally(e);
                return null;
            }
        });
    }

    private OAuthClientService getOAuthService() {
        if (oAuthService == null) {
            oAuthService = oAuthFactory.createOAuthClientService(OAUTH_SERVICE_HANDLE,
                    credentials.tokenUri,
                    credentials.authUri,
                    credentials.clientId,
                    credentials.clientSecret,
                    credentials.scopes.get(0),
                    true);
        }
        return oAuthService;
    }
}
