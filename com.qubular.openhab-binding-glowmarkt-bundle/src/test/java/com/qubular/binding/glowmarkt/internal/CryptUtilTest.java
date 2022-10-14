package com.qubular.binding.glowmarkt.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.service.cm.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class CryptUtilTest {
    private AutoCloseable mockHandle;

    @Mock
    private Configuration configuration;

    private Hashtable<String, Object> props = null;

    @BeforeEach
    public void setup() throws IOException {
        mockHandle = MockitoAnnotations.openMocks(this);

        when(configuration.getProperties()).thenReturn(props);
        doAnswer(invocation -> {
            props = invocation.getArgument(0);
            return null;
        }).when(configuration).update(any(Dictionary.class));
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockHandle.close();
    }

    @Test
    public void encryptAndDecryptRoundTrip() throws GeneralSecurityException, IOException {
        String message = "test message";

        CryptUtil cryptUtil = new CryptUtil(configuration);
        String encrypted = cryptUtil.encrypt(message);
        String decrypted = cryptUtil.decrypt(encrypted);
        assertEquals(message, decrypted);
    }


}