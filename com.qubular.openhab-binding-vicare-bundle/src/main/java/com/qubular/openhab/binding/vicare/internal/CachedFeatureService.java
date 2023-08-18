package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.AuthenticationException;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.model.Feature;
import org.openhab.core.thing.Thing;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.qubular.openhab.binding.vicare.internal.VicareUtil.decodeThingUniqueId;

@Component
public class CachedFeatureService implements FeatureService {
    private static class CachedResponse {

        final CompletableFuture<List<Feature>> response;
        final Instant responseTimestamp;
        public CachedResponse(CompletableFuture<List<Feature>> response, Instant responseTimestamp) {
            this.response = response;
            this.responseTimestamp = responseTimestamp;
        }

    }
    private final VicareService vicareService;
    private final Map<String, CachedResponse> cachedResponses = new HashMap<>();
    @Activate
    public CachedFeatureService(@Reference VicareService vicareService) {
        this.vicareService = vicareService;
    }

    @Override
    public void clear() {
        cachedResponses.clear();
    }

    @Override
    public CompletableFuture<Optional<Feature>> getFeature(Thing thing, String featureName, int expiresInSecs) {
        return getFeatures(thing, expiresInSecs)
                .thenApply(features -> features.stream()
                        .filter(f -> f.getName().equals(featureName))
                        .findFirst());
    }

    @Override
    public synchronized CompletableFuture<List<Feature>> getFeatures(Thing thing, int expiresInSecs) {
        Instant now = Instant.now();
        String key = thing.getUID().getId();
        CachedResponse response = cachedResponses.get(key);
        if (response != null && now.isBefore(response.responseTimestamp.plusSeconds(expiresInSecs - 1))) {
            return response.response;
        }

        VicareUtil.IGD s = decodeThingUniqueId(VicareUtil.getDeviceUniqueId(thing));
        CompletableFuture<List<Feature>> features = new CompletableFuture<>();
        cachedResponses.put(key, new CachedResponse(features, now));
        features.completeAsync(() -> {
            try {
                return vicareService.getFeatures(s.installationId(), s.gatewaySerial(), s.deviceId());
            } catch (AuthenticationException | IOException e) {
                if ((e instanceof AuthenticationException) &&
                        (e.getCause() instanceof InvalidKeyException)) {
                    features.completeExceptionally(new AuthenticationException("Unable to store access token, please check whether your crypto.policy is set to enable full strength encryption or enable limited encryption in Advanced Settings.", (Exception) e.getCause()));
                } else {
                    features.completeExceptionally(e);
                }
                return null;
            }
        });
        return features;
    }
}
