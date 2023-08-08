package com.qubular.openhab.binding.vicare.internal;

import com.qubular.vicare.model.Feature;
import org.openhab.core.thing.Thing;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface FeatureService {
    void clear();

    CompletableFuture<Optional<Feature>> getFeature(Thing thing, String featureName, int expiresInSecs);
    CompletableFuture<List<Feature>> getFeatures(Thing thing, int expiresInSecs);
}
