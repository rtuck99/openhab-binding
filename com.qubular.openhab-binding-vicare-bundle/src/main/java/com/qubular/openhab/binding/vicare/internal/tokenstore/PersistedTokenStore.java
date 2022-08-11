package com.qubular.openhab.binding.vicare.internal.tokenstore;

import com.google.gson.*;
import com.qubular.vicare.TokenStore;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Component(service = TokenStore.class)
public class PersistedTokenStore implements TokenStore {
    private static final String TOKEN_STORE_PID = "com.qubular.openhab.binding.vicare.PersistedTokenStore";
    private static final String PROPERTY_ACCESS_TOKEN = "accessToken";
    public static final String PROPERTY_REFRESH_TOKEN = "refreshToken";

    private final Logger logger = LoggerFactory.getLogger(PersistedTokenStore.class);
    private final ConfigurationAdmin configurationAdmin;

    @Activate
    public PersistedTokenStore(@Reference ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Override
    public AccessToken storeAccessToken(String accessToken, Instant expiry) {
        AccessToken token = new AccessToken(accessToken, expiry);
        try {
            Configuration configuration = configurationAdmin.getConfiguration(TOKEN_STORE_PID);
            if (configuration != null) {
                Dictionary<String, Object> props = ofNullable(configuration.getProperties()).orElseGet(Hashtable::new);
                props.put(PROPERTY_ACCESS_TOKEN, gson().toJson(token));
                configuration.update(props);
            }
        } catch (IOException e) {
            logger.warn("Unable to store access token", e);
        }
        return token;
    }

    private Gson gson() {
        return new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .registerTypeAdapter(Instant.class, new InstantDeserializer())
                .registerTypeAdapter(Instant.class, new InstantSerializer())
                .create();
    }

    private static class InstantDeserializer implements JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return Instant.parse(jsonElement.getAsString());
        }
    }

    private static class InstantSerializer implements JsonSerializer<Instant> {
        @Override
        public JsonElement serialize(Instant instant, Type type, JsonSerializationContext jsonSerializationContext) {
            return jsonSerializationContext.serialize(instant.toString());
        }
    }
    @Override
    public void storeRefreshToken(String refreshToken) {
        try {
            Configuration configuration = configurationAdmin.getConfiguration(TOKEN_STORE_PID);
            if (configuration != null) {
                Dictionary<String, Object> props = ofNullable(configuration.getProperties()).orElseGet(Hashtable::new);
                props.put(PROPERTY_REFRESH_TOKEN, refreshToken);
                configuration.update(props);
            }
        } catch (IOException e) {
            logger.warn("Unable to store refresh token", e);
        }
    }

    @Override
    public Optional<AccessToken> getAccessToken() {
        try {
            Configuration configuration = configurationAdmin.getConfiguration(TOKEN_STORE_PID);
            if (configuration != null) {
                Dictionary<String, Object> props = ofNullable(configuration.getProperties()).orElseGet(Hashtable::new);
                return ofNullable(props.get(PROPERTY_ACCESS_TOKEN))
                        .map(s -> gson().fromJson(String.valueOf(s), AccessToken.class));
            }
        } catch (IOException e) {
            logger.warn("Unable to fetch access token from store.");
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getRefreshToken() {
        try {
            Configuration configuration = configurationAdmin.getConfiguration(TOKEN_STORE_PID);
            if (configuration != null) {
                Dictionary<String, Object> props = ofNullable(configuration.getProperties()).orElseGet(Hashtable::new);
                return ofNullable(props.get(PROPERTY_REFRESH_TOKEN))
                        .map(String::valueOf);
            }
        } catch (IOException e) {
            logger.warn("Unable to fetch refresh token from store.");
        }
        return Optional.empty();
    }
}
