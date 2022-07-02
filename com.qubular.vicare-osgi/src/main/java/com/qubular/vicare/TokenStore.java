package com.qubular.vicare;

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

    void storeAccessToken(String accessToken, Instant expiry);
    void storeRefreshToken(String refreshToken);
    Optional<AccessToken> getAccessToken();
    Optional<String> getRefreshToken();
}
