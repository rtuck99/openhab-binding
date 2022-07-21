package com.qubular.openhab.binding.vicare.internal.challengestore;

import com.qubular.vicare.ChallengeStore;
import org.osgi.service.component.annotations.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Optional.ofNullable;

@Component(service = ChallengeStore.class)
public class BasicChallengeStore implements ChallengeStore<BasicChallenge> {
    private final ConcurrentMap<String, BasicChallenge> challenges = new ConcurrentHashMap<>();

    @Override
    public BasicChallenge createChallenge() {
        var challenge = new BasicChallenge();
        challenges.put(challenge.getKey(), challenge);
        return challenge;
    }

    @Override
    public Optional<BasicChallenge> getChallenge(String challengeKey) {
        return ofNullable(challenges.get(challengeKey));
    }
}
