package com.qubular.openhab.binding.vicare.internal;

import org.osgi.service.cm.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

public class CryptUtil {
    private static final Logger logger = LoggerFactory.getLogger(CryptUtil.class);
    private static final String PW = "40964545-bd87-44db-8459-2003208b1e6a";
    private static final String CONFIG_SALT = "salt";
    public static final String CONFIG_USE_LIMITED_ENCRYPTION = "useLimitedEncryption";
    private static final SecureRandom secureRandom = new SecureRandom();
    private final Configuration configuration;

    private final byte[] salt;

    public CryptUtil(Configuration configuration) {
        this.configuration = configuration;
        this.salt = initializeSalt();
    }

    public String encrypt(String plainText) throws GeneralSecurityException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] iv = sixteenRandomBytes();
        Cipher cipher = initCipher(iv, Cipher.ENCRYPT_MODE);
        outputStream.write(iv);
        outputStream.write(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public String decrypt(String encrypted) throws GeneralSecurityException, IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(encrypted));
        Cipher cipher = initCipher(inputStream.readNBytes(16), Cipher.DECRYPT_MODE);
        byte[] output = cipher.doFinal(inputStream.readAllBytes());
        return new String(output, StandardCharsets.UTF_8);
    }

    private Cipher initCipher(byte[] initializationVector, int opmode) throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            int iterations = 256;
            KeySpec spec = new PBEKeySpec(PW.toCharArray(), salt, iterations, getUseLimitedEncryption() ? 128 : 256);
            SecretKey secretKey = keyFactory.generateSecret(spec);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getEncoded(), "AES");
            cipher.init(opmode, secretKeySpec, new IvParameterSpec(initializationVector));
            return cipher;
    }

    private byte[] initializeSalt() {
        Dictionary<String, Object> props = getProperties();
        byte[] salt = (byte[]) props.get(CONFIG_SALT);
        if (salt == null) {
            salt = sixteenRandomBytes();
            props.put(CONFIG_SALT, salt);
            try {
                configuration.update(props);
            } catch (IOException e) {
                throw new RuntimeException("Unable to initialize salt", e);
            }
        }
        return salt;
    }

    private boolean getUseLimitedEncryption() {
        return requireNonNullElse((Boolean) getProperties().get(CONFIG_USE_LIMITED_ENCRYPTION), false);
    }

    private Dictionary<String, Object> getProperties() {
        return ofNullable(configuration.getProperties()).orElseGet(Hashtable::new);
    }

    private static byte[] sixteenRandomBytes() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}
