package com.qubular.openhab.binding.vicare.internal.tokenstore;

import com.qubular.vicare.TokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.EventAdmin;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Instant;
import java.util.Dictionary;
import java.util.Hashtable;

import static com.qubular.openhab.binding.vicare.internal.CryptUtil.CONFIG_USE_LIMITED_ENCRYPTION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

@EnabledIfSystemProperty(named="test-limited-crypto", matches="true")
class PersistedTokenStoreTest {
    @Mock
    private ConfigurationAdmin configurationAdmin;
    @Mock
    private EventAdmin eventAdmin;
    @Mock
    private Configuration configuration;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        doReturn(configuration).when(configurationAdmin).getConfiguration(anyString());
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void constructorSucceedsIfCryptoPolicyLimited() {
        Security.setProperty("crypto.policy", "limited");
        new PersistedTokenStore(configurationAdmin, eventAdmin);
    }

    @Test
    public void storeAccessTokenFailsIfCryptoPolicyLimited() {
        Security.setProperty("crypto.policy", "limited");
        PersistedTokenStore persistedTokenStore = new PersistedTokenStore(configurationAdmin, eventAdmin);
        assertThrows(GeneralSecurityException.class, () -> persistedTokenStore.storeAccessToken("testtoken", Instant.now().plusSeconds(60)));
    }

    @Test
    public void storeAccessTokenSucceedsIfCryptoPolicyLimitedAndUseLimitedEncryption() throws GeneralSecurityException {
        Security.setProperty("crypto.policy", "limited");
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(CONFIG_USE_LIMITED_ENCRYPTION, true);
        doReturn(props).when(configuration).getProperties();

        PersistedTokenStore persistedTokenStore = new PersistedTokenStore(configurationAdmin, eventAdmin);
        TokenStore.AccessToken accessToken = persistedTokenStore.storeAccessToken("testtoken",
                                                                                Instant.now().plusSeconds(60));
        assertNotNull(accessToken);
    }

    @Test
    public void storeRefreshTokenSucceedsIfCryptoPolicyLimitedAndUseLimitedEncryption() throws GeneralSecurityException {
        Security.setProperty("crypto.policy", "limited");
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(CONFIG_USE_LIMITED_ENCRYPTION, true);
        doReturn(props).when(configuration).getProperties();

        PersistedTokenStore persistedTokenStore = new PersistedTokenStore(configurationAdmin, eventAdmin);
        persistedTokenStore.storeRefreshToken("refreshtoken");
    }
}