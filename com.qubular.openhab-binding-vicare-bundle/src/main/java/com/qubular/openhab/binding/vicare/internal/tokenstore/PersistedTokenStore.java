package com.qubular.openhab.binding.vicare.internal.tokenstore;

import com.qubular.vicare.TokenStore;
import org.osgi.service.component.annotations.Component;

import java.time.Instant;
import java.util.Optional;

@Component(service = TokenStore.class)
public class PersistedTokenStore implements TokenStore {
    @Override
    public AccessToken storeAccessToken(String accessToken, Instant expiry) {
        return null;
    }

    @Override
    public void storeRefreshToken(String refreshToken) {

    }

    @Override
    public Optional<AccessToken> getAccessToken() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getRefreshToken() {
        return Optional.empty();
    }
}
