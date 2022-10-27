package com.qubular.vicare;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Optional;

public interface TokenStore {
    class AccessToken {
        public final String token;
        public final Instant expiry;

        public AccessToken(String token, Instant expiry) {
            this.token = token;
            this.expiry = expiry;
        }
    }

    AccessToken storeAccessToken(String accessToken, Instant expiry) throws GeneralSecurityException;
    void storeRefreshToken(String refreshToken) throws GeneralSecurityException;
    Optional<AccessToken> getAccessToken() throws GeneralSecurityException;
    Optional<String> getRefreshToken();
}
