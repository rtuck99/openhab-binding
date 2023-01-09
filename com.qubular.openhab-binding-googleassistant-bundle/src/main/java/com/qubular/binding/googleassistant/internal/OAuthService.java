package com.qubular.binding.googleassistant.internal;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;

import javax.servlet.http.HttpSession;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides a means to obtain OAuth access token and refresh token as per
 * https://developers.google.com/identity/protocols/oauth2/limited-input-device
 */
public interface OAuthService {
    String ACCESS_TYPE_OFFLINE = "offline";

    class OAuthException extends Exception {
        public static final String ACCESS_DENIED = "access_denied";
        public static final String AUTHORISATION_PENDING = "authorization_pending";
        public static final String SLOW_DOWN = "slow_down";
        public static final String INVALID_CLIENT = "invalid_client";
        public static final String INVALID_GRANT = "invalid_grant";
        public static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
        private String error;

        public String getError() {
            return error;
        }

        public OAuthException(AuthorisationError error) {
            super(error.errorDescription);
            this.error = error.error;
        }

        public OAuthException(String message, Throwable t) {
            super(message, t);
        }
    }

    class OAuthSession {
        private static final String ATTRIBUTE_ACCESS_TOKEN = "googleAssistant.oAuthAccessToken";

        public final AccessTokenResponse accessTokenResponse;

        public OAuthSession(AccessTokenResponse accessTokenResponse) {
            this.accessTokenResponse = accessTokenResponse;
        }

        public String getAccessToken() {
            return accessTokenResponse == null ? null : accessTokenResponse.getAccessToken();
        }

        public String getRefreshToken() {
            return accessTokenResponse == null ? null : accessTokenResponse.getRefreshToken();
        }

        /** Base time from which expiry is measured. */
        public Instant getBaseTime() {
            return accessTokenResponse.getCreatedOn();
        }

        /** Remaining lifetime of token in seconds. */
        public int getExpiresIn() {
            return (int) accessTokenResponse.getExpiresIn();
        }

        public void saveToSession(HttpSession session) {
            session.setAttribute(ATTRIBUTE_ACCESS_TOKEN, accessTokenResponse);
        }

        public static OAuthSession loadFromSession(HttpSession session) {
            if (session == null) {
                return null;
            }
            AccessTokenResponse tokenResponse = (AccessTokenResponse) session.getAttribute(ATTRIBUTE_ACCESS_TOKEN);
            if (tokenResponse == null) {
                return null;
            }

            return new OAuthSession(tokenResponse);
        }
    }

    class Authorisation {
        /** Base timestamp from which expiry is measured. */
        public Instant baseTime;
        /** Identifies this instance of the app requesting the authorisation */
        public String deviceCode;
        /** Time in s that this authorisation code expires in */
        public int expiresIn;
        /** Time in s to wait between polling requests */
        public int interval;
        /** Code that the user must enter on their other device. */
        public String userCode;
        /** URL for the user to visit to enter the code. */
        public String verificationUrl;
    }

    class AuthorisationError {
        public String error;
        public String errorDescription;
    }

    class ClientCredentials {
        public String clientId;
        public List<String> scopes = Collections.emptyList();
        public String clientSecret;
        public String projectId;
        public String authUri;
        public String tokenUri;
        public String authProviderX509CertUrl;
        public List<String> redirectUris = Collections.emptyList();

        private static class JsonWrapper {
            public ClientCredentials web;
        }

        public ClientCredentials() {
        }

        public ClientCredentials(String clientId, List<String> scopes, String clientSecret, String authUri, String tokenUri) {
            this.clientId = clientId;
            this.scopes = scopes;
            this.clientSecret = clientSecret;
            this.authUri = authUri;
            this.tokenUri = tokenUri;
        }

        public static ClientCredentials fromJson(String string) {
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            return gson.fromJson(string, JsonWrapper.class).web;
        }
    }

    void setCredentials(ClientCredentials credentials);

    URI getAuthorisationUri(String accessType, URI redirectUri);

    CompletableFuture<OAuthSession> accessTokenFromAuthz(URI redirectUri, String authorisation);

    /**
     * Get the current access token, or refresh it if it should be refreshed.
     * @param session
     * @return the session
     */
    CompletableFuture<OAuthSession> maybeRefreshAccessToken(@Nullable OAuthSession session);
}
