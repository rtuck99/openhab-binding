package com.qubular.glowmarkt.impl;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.qubular.glowmarkt.*;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

@Component(service = GlowmarktService.class)
public class GlowmarktServiceImpl implements GlowmarktService {
    private static final Logger logger = LoggerFactory.getLogger(GlowmarktServiceImpl.class);
    public static final String HEADER_APPLICATION_ID = "applicationId";
    public static final String HEADER_TOKEN = "token";
    public static final String QUERY_PARAM_FROM = "from";
    public static final String QUERY_PARAM_TO = "to";
    public static final String QUERY_PARAM_PERIOD = "period";
    public static final String QUERY_PARAM_OFFSET = "offset";
    public static final String QUERY_PARAM_FUNCTION = "function";
    public static final DateTimeFormatter YYYYMMDDTHHMMSS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static class AuthRequest {
        String username;
        String password;

        public AuthRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private static class AuthResponse {
        String token;
        Instant exp;
    }

    private static class ReadingResponse {
        List<double[]> data;
    }

    private static class Data {
        Instant firstTs;
        Instant lastTs;
    }

    private static class FirstTimeResponse {
        Data data;
    }

    private static class LastTimeResponse {
        Data data;
    }

    private final Gson gson;

    @Activate
    public GlowmarktServiceImpl() {
        gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class,
                        (JsonDeserializer<Instant>) (jsonElement, type, jsonDeserializationContext) -> Instant.ofEpochSecond(jsonElement.getAsLong()))
                .create();
    }


    @Override
    public GlowmarktSession authenticate(GlowmarktSettings settings, String aUsername, String aPassword) throws IOException, AuthenticationFailedException {
        URI authUri = settings.getApiEndpoint().resolve("auth");
        logger.trace("Sending authentication request to {}", authUri);
        String json = gson.toJson(new AuthRequest(aUsername, aPassword));
        logger.trace("Sending content {}", json);
        try {
            ContentResponse response = settings.getHttpClient()
                    .POST(authUri)
                    .content(new StringContentProvider("application/json", json, StandardCharsets.UTF_8))
                    .header(HEADER_APPLICATION_ID, settings.getApplicationId())
                    .send();
            logger.trace("Authenticate response: {}", response.getContentAsString());
            if (response.getStatus() == OK_200) {
                AuthResponse authResponse = gson.fromJson(response.getContentAsString(), AuthResponse.class);
                return new GlowmarktSession(authResponse.exp,
                        authResponse.token);
            } else {
                throw exceptionForHttpResponseError(response, "Unable to authenticate");
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new IOException("Unable to authenticate", e);
        }
    }

    @Override
    public List<VirtualEntity> getVirtualEntities(GlowmarktSession session, GlowmarktSettings settings) throws IOException, AuthenticationFailedException {
        validateToken(session);
        URI virtualEntityUri = settings.getApiEndpoint().resolve("virtualentity");
        logger.trace("Sending virtualentity request to {}", virtualEntityUri);
        try {
            ContentResponse response = settings.getHttpClient()
                    .newRequest(virtualEntityUri)
                    .header(HEADER_APPLICATION_ID, settings.getApplicationId())
                    .header(HEADER_TOKEN, session.getToken())
                    .method(HttpMethod.GET)
                    .send();
            if (response.getStatus() == OK_200) {
                List<VirtualEntity> virtualEntities = gson.fromJson(response.getContentAsString(),
                        new TypeToken<List<VirtualEntity>>() {}.getType());
                return virtualEntities;
            } else {
                throw exceptionForHttpResponseError(response, "Unable to fetch virtual entities");
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw exceptionForHttpClientException(e, "Unable to fetch virtual entities");
        }
    }

    @Override
    public VirtualEntity getVirtualEntity(GlowmarktSession session, GlowmarktSettings settings, String virtualEntityId) throws IOException, AuthenticationFailedException {
        validateToken(session);
        URI virtualEntityUri = settings.getApiEndpoint().resolve(format("virtualentity/%s/resources", URLEncoder.encode(virtualEntityId, StandardCharsets.UTF_8)));
        logger.trace("Sending virtual entity resource request to {}", virtualEntityUri);
        try {
            ContentResponse response = settings.getHttpClient()
                    .newRequest(virtualEntityUri)
                    .header(HEADER_APPLICATION_ID, settings.getApplicationId())
                    .header(HEADER_TOKEN, session.getToken())
                    .method(HttpMethod.GET)
                    .send();
            if (response.getStatus() == OK_200) {
                VirtualEntity virtualEntity = gson.fromJson(response.getContentAsString(), VirtualEntity.class);
                return virtualEntity;
            } else {
                throw exceptionForHttpResponseError(response, "Unable to fetch virtual entity");
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw exceptionForHttpClientException(e, "Unable to fetch virtual entity");
        }
    }

    @Override
    public List<ResourceData> getResourceReadings(GlowmarktSession session, GlowmarktSettings settings, String resourceId, Instant from, Instant to, AggregationPeriod period, AggregationFunction aggregationFunction) throws IOException, AuthenticationFailedException {
        validateToken(session);
        URI readingsUri = settings.getApiEndpoint().resolve(format("resource/%s/readings", URLEncoder.encode(resourceId, StandardCharsets.UTF_8)));
        logger.trace("Sending reading resource request to {}, from {} to {}", readingsUri, from, to);
        try {
            ContentResponse response = settings.getHttpClient()
                    .newRequest(readingsUri)
                    .header(HEADER_APPLICATION_ID, settings.getApplicationId())
                    .header(HEADER_TOKEN, session.getToken())
                    .param(QUERY_PARAM_FROM, LocalDateTime.ofInstant(from, ZoneOffset.UTC).format(YYYYMMDDTHHMMSS))
                    .param(QUERY_PARAM_TO, LocalDateTime.ofInstant(to, ZoneOffset.UTC).format(YYYYMMDDTHHMMSS))
                    .param(QUERY_PARAM_PERIOD, period.name())
                    .param(QUERY_PARAM_OFFSET, String.valueOf(0))
                    .param(QUERY_PARAM_FUNCTION, aggregationFunction.getValue())
                    .method(HttpMethod.GET)
                    .send();
            logger.trace("Sent request");
            if (response.getStatus() == OK_200) {
                ReadingResponse readingResponse = gson.fromJson(response.getContentAsString(), ReadingResponse.class);
                return readingResponse.data.stream()
                        .map(dataPairs -> new ResourceData(dataPairs[1], Instant.ofEpochSecond((long)dataPairs[0])))
                        .collect(Collectors.toList());
            } else {
                throw exceptionForHttpResponseError(response, "Unable to fetch reading data");
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw exceptionForHttpClientException(e, "Unable to fetch reading data");
        }
    }

    @Override
    public void catchup(GlowmarktSession session, GlowmarktSettings settings, String resourceId) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant getFirstTime(GlowmarktSession session, GlowmarktSettings settings, String resourceId) throws AuthenticationFailedException, IOException {
        validateToken(session);
        URI uri = settings.getApiEndpoint().resolve(format("resource/%s/first-time", resourceId));
        logger.trace("Fetching first time data for {}", resourceId);
        try {
            ContentResponse response = settings.getHttpClient()
                    .newRequest(uri)
                    .header(HEADER_APPLICATION_ID, settings.getApplicationId())
                    .header(HEADER_TOKEN, session.getToken())
                    .method(HttpMethod.GET)
                    .send();
            if (response.getStatus() == 200) {
                FirstTimeResponse firstTimeResponse = gson.fromJson(response.getContentAsString(), FirstTimeResponse.class);
                return firstTimeResponse.data.firstTs;
            } else {
                throw exceptionForHttpResponseError(response, "Unable to fetch first time data");
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw exceptionForHttpClientException(e, format("Unable to fetch first time data for %s", resourceId));
        }
    }

    @Override
    public Instant getLastTime(GlowmarktSession session, GlowmarktSettings settings, String resourceId) throws AuthenticationFailedException, IOException {
        validateToken(session);
        URI uri = settings.getApiEndpoint().resolve(format("resource/%s/last-time", resourceId));
        logger.trace("Fetching last time data for {}", resourceId);
        try {
            ContentResponse response = settings.getHttpClient()
                    .newRequest(uri)
                    .header(HEADER_APPLICATION_ID, settings.getApplicationId())
                    .header(HEADER_TOKEN, session.getToken())
                    .method(HttpMethod.GET)
                    .send();
            if (response.getStatus() == 200) {
                LastTimeResponse lastTimeResponse = gson.fromJson(response.getContentAsString(), LastTimeResponse.class);
                return lastTimeResponse.data.lastTs;
            } else {
                throw exceptionForHttpResponseError(response, "Unable to fetch last time data");
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw exceptionForHttpClientException(e, format("Unable to fetch last time data for %s", resourceId));
        }
    }

    private IOException exceptionForHttpResponseError(ContentResponse response, String message) throws IOException, AuthenticationFailedException {
        message += ", server responded with " + response.getStatus();
        logger.debug(message);
        if (response.getStatus() == 401) {
            throw new AuthenticationFailedException(message);
        }
        throw new IOException(message);
    }

    private IOException exceptionForHttpClientException(Throwable t, String message) throws IOException {
        logger.debug(message);
        throw new IOException(message, t);
    }

    private void validateToken(GlowmarktSession session) throws AuthenticationFailedException {
        if (!session.getExpiry().isAfter(Instant.now())) {
            throw new AuthenticationFailedException("Web token is expired");
        }
    }
}
