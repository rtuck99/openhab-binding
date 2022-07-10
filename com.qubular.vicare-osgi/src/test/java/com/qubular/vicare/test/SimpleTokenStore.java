package com.qubular.vicare.test;

import com.qubular.vicare.TokenStore;
import org.osgi.service.component.annotations.Component;

import java.time.Instant;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Component(service = TokenStore.class)
public class SimpleTokenStore implements TokenStore {
    AccessToken accessToken;
    String refreshToken;

    @Override
    public AccessToken storeAccessToken(String accessToken, Instant expiry) {
        this.accessToken = new AccessToken(accessToken, expiry);
        return this.accessToken;
    }

    @Override
    public void storeRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public Optional<AccessToken> getAccessToken() {
        return ofNullable(accessToken);
    }

    @Override
    public Optional<String> getRefreshToken() {
        return ofNullable(refreshToken);
    }

    void reset() {
        accessToken = null;
        refreshToken = null;
    }
}
